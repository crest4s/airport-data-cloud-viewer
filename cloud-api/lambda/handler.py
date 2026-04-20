import json
import os
import uuid
import boto3
from datetime import datetime, timezone

dynamodb = boto3.resource('dynamodb')
TABLE_NAME = os.environ.get('TABLE_NAME', 'airport-results')


def lambda_handler(event, context):
    # Function URL uses requestContext.http.method; API GW uses httpMethod
    method = (
        event.get('requestContext', {}).get('http', {}).get('method')
        or event.get('httpMethod', 'GET')
    )

    if method == 'OPTIONS':
        return _response(200, {})
    elif method == 'POST':
        return _handle_post(event)
    elif method == 'GET':
        return _handle_get()
    else:
        return _response(405, {'error': 'Method not allowed'})


def _handle_post(event):
    try:
        body = json.loads(event.get('body') or '{}')
        table = dynamodb.Table(TABLE_NAME)

        item = {
            'id':         str(uuid.uuid4()),
            'phase':      str(body.get('phase', '')),
            'parameters': str(body.get('parameters', '')),
            'result':     str(body.get('result', '')),
            'timestamp':  str(body.get('timestamp', datetime.now(timezone.utc).isoformat())),
            'username':   str(body.get('username', '')),
        }

        table.put_item(Item=item)
        return _response(200, {'message': 'OK', 'id': item['id']})
    except Exception as exc:
        return _response(500, {'error': str(exc)})


def _handle_get():
    try:
        table = dynamodb.Table(TABLE_NAME)
        scan_result = table.scan()
        items = sorted(
            scan_result.get('Items', []),
            key=lambda x: x.get('timestamp', ''),
            reverse=True
        )
        return _response(200, items)
    except Exception as exc:
        return _response(500, {'error': str(exc)})


def _response(status_code, body):
    return {
        'statusCode': status_code,
        'headers': {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type',
        },
        'body': json.dumps(body, default=str),
    }
