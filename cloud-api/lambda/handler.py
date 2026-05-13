import json
import os
import uuid
import boto3
from datetime import datetime, timezone
from boto3.dynamodb.conditions import Attr

dynamodb = boto3.resource('dynamodb')
TABLE_NAME = os.environ.get('TABLE_NAME', 'airport-results')
VALID_PHASES = {'Phase01', 'Phase02', 'Phase03', 'Phase04'}


def lambda_handler(event, context):
    method = (
        event.get('requestContext', {}).get('http', {}).get('method')
        or event.get('httpMethod', 'GET')
    )

    if method == 'OPTIONS':
        return _response(200, {})
    elif method == 'POST':
        return _handle_post(event)
    elif method == 'GET':
        return _handle_get(event)
    elif method == 'DELETE':
        return _handle_delete(event)
    else:
        return _response(405, {'error': 'Method not allowed'})


def _handle_post(event):
    try:
        raw = event.get('body') or '{}'
        if len(raw) > 256 * 1024:
            return _response(413, {'error': 'Payload too large (max 256 KB)'})

        body = json.loads(raw)

        phase    = str(body.get('phase', ''))
        username = str(body.get('username', ''))[:64].strip()

        if not phase or phase not in VALID_PHASES:
            return _response(400, {
                'error': f'Invalid or missing phase. Must be one of {sorted(VALID_PHASES)}'
            })
        if not username:
            return _response(400, {'error': 'username is required'})

        item = {
            'id':           str(uuid.uuid4()),
            'phase':        phase,
            'parameters':   str(body.get('parameters', ''))[:512],
            'result':       str(body.get('result', ''))[:2048],
            'full_details': json.dumps(body.get('full_details', []), default=str),
            'timestamp':    str(body.get('timestamp', datetime.now(timezone.utc).isoformat())),
            'username':     username,
        }

        dynamodb.Table(TABLE_NAME).put_item(Item=item)
        return _response(201, {'message': 'Created', 'id': item['id']})

    except json.JSONDecodeError:
        return _response(400, {'error': 'Invalid JSON body'})
    except Exception as e:
        return _response(500, {'error': str(e)})


def _handle_get(event):
    try:
        params       = event.get('queryStringParameters') or {}
        phase_filter = params.get('phase', '')
        limit        = min(int(params.get('limit', 500)), 1000)

        table  = dynamodb.Table(TABLE_NAME)
        kwargs = {}
        if phase_filter in VALID_PHASES:
            kwargs['FilterExpression'] = Attr('phase').eq(phase_filter)

        items = []
        resp  = table.scan(**kwargs)
        items.extend(resp.get('Items', []))
        while 'LastEvaluatedKey' in resp and len(items) < limit:
            kwargs['ExclusiveStartKey'] = resp['LastEvaluatedKey']
            resp = table.scan(**kwargs)
            items.extend(resp.get('Items', []))

        items = sorted(items, key=lambda x: x.get('timestamp', ''), reverse=True)[:limit]
        return _response(200, {'items': items, 'total': len(items)})

    except Exception as e:
        return _response(500, {'error': str(e)})


def _handle_delete(event):
    try:
        params  = event.get('queryStringParameters') or {}
        item_id = params.get('id', '').strip()
        if not item_id:
            return _response(400, {'error': 'id query parameter is required'})

        dynamodb.Table(TABLE_NAME).delete_item(Key={'id': item_id})
        return _response(200, {'message': 'Deleted', 'id': item_id})

    except Exception as e:
        return _response(500, {'error': str(e)})


def _response(status_code, body):
    return {
        'statusCode': status_code,
        'headers': {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET,POST,DELETE,OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type',
        },
        'body': json.dumps(body, default=str),
    }
