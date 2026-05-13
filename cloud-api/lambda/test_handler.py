"""
Unit tests for handler.py — mocks DynamoDB so no AWS credentials needed.
Run: pytest test_handler.py -v
"""
import json
from unittest.mock import MagicMock, patch
import pytest
import handler


# ── Helpers ──────────────────────────────────────────────────────────────────

def _event(method, body=None, qs=None):
    e = {'requestContext': {'http': {'method': method}}}
    if body is not None:
        e['body'] = json.dumps(body)
    if qs:
        e['queryStringParameters'] = qs
    return e


def _mock_table(items=None):
    table = MagicMock()
    table.scan.return_value = {'Items': items or []}
    return table


# ── OPTIONS ───────────────────────────────────────────────────────────────────

def test_options_returns_200():
    res = handler.lambda_handler(_event('OPTIONS'), None)
    assert res['statusCode'] == 200


# ── Unsupported method ─────────────────────────────────────────────────────────

def test_unknown_method_returns_405():
    res = handler.lambda_handler(_event('PUT'), None)
    assert res['statusCode'] == 405


# ── POST validation ───────────────────────────────────────────────────────────

@patch('handler.dynamodb')
def test_post_missing_phase(mock_db):
    res = handler.lambda_handler(_event('POST', {'username': 'alice'}), None)
    assert res['statusCode'] == 400
    assert 'phase' in json.loads(res['body'])['error'].lower()


@patch('handler.dynamodb')
def test_post_invalid_phase(mock_db):
    res = handler.lambda_handler(_event('POST', {'phase': 'Phase99', 'username': 'alice'}), None)
    assert res['statusCode'] == 400


@patch('handler.dynamodb')
def test_post_missing_username(mock_db):
    res = handler.lambda_handler(_event('POST', {'phase': 'Phase01'}), None)
    assert res['statusCode'] == 400
    assert 'username' in json.loads(res['body'])['error'].lower()


@patch('handler.dynamodb')
def test_post_valid_creates_item(mock_db):
    table = _mock_table()
    mock_db.Table.return_value = table

    payload = {'phase': 'Phase01', 'username': 'alice', 'parameters': 'threshold=30', 'result': '42 flights'}
    res = handler.lambda_handler(_event('POST', payload), None)

    assert res['statusCode'] == 201
    body = json.loads(res['body'])
    assert 'id' in body
    assert body['message'] == 'Created'
    table.put_item.assert_called_once()


@patch('handler.dynamodb')
def test_post_all_valid_phases(mock_db):
    mock_db.Table.return_value = _mock_table()
    for phase in ('Phase01', 'Phase02', 'Phase03', 'Phase04'):
        res = handler.lambda_handler(_event('POST', {'phase': phase, 'username': 'u'}), None)
        assert res['statusCode'] == 201, f"Phase {phase} should be accepted"


@patch('handler.dynamodb')
def test_post_invalid_json_returns_400(mock_db):
    e = {'requestContext': {'http': {'method': 'POST'}}, 'body': 'not-json'}
    res = handler.lambda_handler(e, None)
    assert res['statusCode'] == 400


@patch('handler.dynamodb')
def test_post_payload_too_large_returns_413(mock_db):
    e = {'requestContext': {'http': {'method': 'POST'}}, 'body': 'x' * (256 * 1024 + 1)}
    res = handler.lambda_handler(e, None)
    assert res['statusCode'] == 413


# ── GET ───────────────────────────────────────────────────────────────────────

@patch('handler.dynamodb')
def test_get_returns_items_and_total(mock_db):
    items = [
        {'id': '1', 'phase': 'Phase01', 'username': 'a', 'timestamp': '2025-01-01T00:00:00'},
        {'id': '2', 'phase': 'Phase02', 'username': 'b', 'timestamp': '2025-01-02T00:00:00'},
    ]
    mock_db.Table.return_value = _mock_table(items)

    res = handler.lambda_handler(_event('GET'), None)
    assert res['statusCode'] == 200
    body = json.loads(res['body'])
    assert 'items' in body
    assert body['total'] == 2
    assert body['items'][0]['id'] == '2'  # sorted desc by timestamp


@patch('handler.dynamodb')
def test_get_empty_table(mock_db):
    mock_db.Table.return_value = _mock_table([])
    res = handler.lambda_handler(_event('GET'), None)
    assert res['statusCode'] == 200
    body = json.loads(res['body'])
    assert body['items'] == []
    assert body['total'] == 0


@patch('handler.dynamodb')
def test_get_phase_filter_passed_to_scan(mock_db):
    table = _mock_table([])
    mock_db.Table.return_value = table

    res = handler.lambda_handler(_event('GET', qs={'phase': 'Phase01'}), None)
    assert res['statusCode'] == 200
    # FilterExpression must have been used
    call_kwargs = table.scan.call_args[1]
    assert 'FilterExpression' in call_kwargs


@patch('handler.dynamodb')
def test_get_invalid_phase_filter_ignored(mock_db):
    table = _mock_table([])
    mock_db.Table.return_value = table

    res = handler.lambda_handler(_event('GET', qs={'phase': 'BadPhase'}), None)
    assert res['statusCode'] == 200
    call_kwargs = table.scan.call_args[1]
    assert 'FilterExpression' not in call_kwargs


# ── DELETE ────────────────────────────────────────────────────────────────────

@patch('handler.dynamodb')
def test_delete_without_id_returns_400(mock_db):
    res = handler.lambda_handler(_event('DELETE'), None)
    assert res['statusCode'] == 400


@patch('handler.dynamodb')
def test_delete_with_id_removes_item(mock_db):
    table = _mock_table()
    mock_db.Table.return_value = table

    res = handler.lambda_handler(_event('DELETE', qs={'id': 'abc-123'}), None)
    assert res['statusCode'] == 200
    body = json.loads(res['body'])
    assert body['id'] == 'abc-123'
    table.delete_item.assert_called_once_with(Key={'id': 'abc-123'})


# ── CORS headers ──────────────────────────────────────────────────────────────

@patch('handler.dynamodb')
def test_cors_headers_present(mock_db):
    mock_db.Table.return_value = _mock_table([])
    res = handler.lambda_handler(_event('GET'), None)
    assert res['headers']['Access-Control-Allow-Origin'] == '*'
    assert 'DELETE' in res['headers']['Access-Control-Allow-Methods']
