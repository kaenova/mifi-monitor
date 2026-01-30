# MIFI Dashboard API Documentation

## Overview
This document describes how to programmatically scrape data from the LTE Wireless MIFI device at `http://192.168.50.1/`.

## Authentication
The MIFI device uses **Digest Authentication**. You'll need to authenticate before accessing the API endpoints.

### Login Flow
1. Get the nonce and realm from the device
2. Calculate the digest response using MD5
3. Include the authorization header in subsequent requests

## API Endpoints

### 1. Homepage Info Endpoint
**URL:** `http://192.168.50.1/xml_action.cgi?method=get&module=duster&file=json_homepage_info{timestamp}`

**Response Fields:**
| Field | Description | Example |
|-------|-------------|---------|
| `network_name` | Network operator | "IND TELKOMSEL" |
| `mac` | MAC address | "e4:7d:eb:a3:8a:17" |
| `imei` | IMEI number | "354144810275881" |
| `sw_version` | Software version | "LB02ITsel_M0263A_M21_LED_V004" |
| `msisdn` | Phone number | "085156305737" |
| `lan_ip` | MIFI LAN IP | "192.168.50.1" |
| `ssid` | SSID (hex encoded) | "006b00610065005f006d006900660069" |

### 2. Status Info Endpoint
**URL:** `http://192.168.50.1/xml_action.cgi?method=get&module=duster&file=json_status_info{timestamp}`

**Response Fields:**
| Field | Description | Example | Notes |
|-------|-------------|---------|-------|
| `rssi` | Signal Strength (dBm) | "-70" | |
| `signal_quality` | Signal Quality | "5" | Scale: 0-5 |
| `sys_mode` | Network Mode | "17" | See Network Modes below |
| `wifi_clients_num` | Connected Devices | "3" | |
| `run_seconds` | Runtime (seconds) | "15617" | Convert to hours/minutes |
| `battery_percent` | Battery Level | "90" | Percentage |
| `battery_charging` | Charging Status | "0" | 0 = No, 1 = Yes |
| `tx_byte_all` | Total Sent Data | "7560750215" | Bytes |
| `rx_byte_all` | Total Received Data | "94488146672" | Bytes |
| `tx_speed` | Current Upload Speed | "0" | KB/s (may need conversion) |
| `rx_speed` | Current Download Speed | "0" | KB/s (may need conversion) |

## Network Mode Mapping
| sys_mode | Network |
|----------|---------|
| 17 | 4G |

## Python Example

```python
import requests
import hashlib
import time
import json

MIFI_IP = "192.168.50.1"
USERNAME = "admin"
PASSWORD = "admin"

def parse_digest_auth(header, method, uri, username, password):
    """Parse WWW-Authenticate header and create Authorization header"""
    realm = None
    nonce = None
    qop = None
    
    parts = header.split(', ')
    for part in parts:
        key, value = part.split('=')
        if key.strip() == 'Digest realm':
            realm = value.strip('"')
        elif key.strip() == 'nonce':
            nonce = value.strip('"')
        elif key.strip() == 'qop':
            qop = value.strip('"')
    
    # Calculate response
    ha1 = hashlib.md5(f"{username}:{realm}:{password}".encode()).hexdigest()
    ha2 = hashlib.md5(f"{method}:{uri}".encode()).hexdigest()
    
    cnonce = "test"
    nc = "00000001"
    
    response = hashlib.md5(f"{ha1}:{nonce}:{nc}:{cnonce}:{qop}:{ha2}".encode()).hexdigest()
    
    return f'Digest username="{username}", realm="{realm}", nonce="{nonce}", uri="{uri}", response="{response}", qop={qop}, nc={nc}, cnonce="{cnonce}"'

def get_mifi_data():
    session = requests.Session()
    
    # Step 1: Get homepage to trigger auth
    response = session.get(f"http://{MIFI_IP}/")
    
    # Step 2: Parse auth and login
    auth_header = parse_digest_auth(
        response.headers.get('WWW-Authenticate'),
        'GET',
        '/cgi/xml_action.cgi',
        USERNAME,
        PASSWORD
    )
    
    session.headers.update({'Authorization': auth_header})
    
    # Step 3: Get homepage info
    timestamp = int(time.time() * 1000)
    homepage_url = f"http://{MIFI_IP}/xml_action.cgi?method=get&module=duster&file=json_homepage_info{timestamp}"
    homepage = session.get(homepage_url).json()
    
    # Step 4: Get status info
    status_url = f"http://{MIFI_IP}/xml_action.cgi?method=get&module=duster&file=json_status_info{timestamp}"
    status = session.get(status_url).json()
    
    return homepage, status

def format_mifi_data(homepage, status):
    """Format and combine data from both endpoints"""
    
    # Convert hex SSID to string
    ssid_hex = homepage['ssid']
    ssid = bytes.fromhex(ssid_hex).decode('utf-16be')
    
    # Convert runtime to hours, minutes, seconds
    runtime_seconds = int(status['run_seconds'])
    hours = runtime_seconds // 3600
    minutes = (runtime_seconds % 3600) // 60
    seconds = runtime_seconds % 60
    
    # Convert bytes to GB
    sent_gb = int(status['tx_byte_all']) / (1024**3)
    received_gb = int(status['rx_byte_all']) / (1024**3)
    
    # Network mode mapping
    sys_mode = int(status['sys_mode'])
    network_mode = "4G" if sys_mode == 17 else "Unknown"
    
    return {
        "signal_strength": f"{status['rssi']} dBm",
        "signal_quality": int(status['signal_quality']),
        "network_mode": network_mode,
        "operator": homepage['network_name'],
        "connected_devices": int(status['wifi_clients_num']),
        "runtime": f"{hours}h {minutes}m {seconds}s",
        "battery_percent": int(status['battery_percent']),
        "battery_charging": bool(int(status['battery_charging'])),
        "sent_data": f"{sent_gb:.3f}GB",
        "received_data": f"{received_gb:.3f}GB",
        "current_upload_speed": f"{status['tx_speed']} KB/s",
        "current_download_speed": f"{status['rx_speed']} KB/s",
        "ssid": ssid,
        "imei": homepage['imei'],
        "mac": homepage['mac'],
        "phone_number": homepage['msisdn'],
        "software_version": homepage['sw_version']
    }

if __name__ == "__main__":
    homepage, status = get_mifi_data()
    data = format_mifi_data(homepage, status)
    print(json.dumps(data, indent=2))
```

## JavaScript/Node.js Example

```javascript
const axios = require('axios');
const crypto = require('crypto');
const cheerio = require('cheerio');

const MIFI_IP = '192.168.50.1';
const USERNAME = 'admin';
const PASSWORD = 'admin';

async function getMifiData() {
    const instance = axios.create({
        baseURL: `http://${MIFI_IP}`,
        timeout: 5000
    });

    // Get homepage to trigger auth
    const response = await instance.get('/');
    const wwwAuth = response.headers['www-authenticate'];
    
    // Parse digest auth
    const realm = wwwAuth.match(/realm="([^"]+)"/)[1];
    const nonce = wwwAuth.match(/nonce="([^"]+)"/)[1];
    const qop = wwwAuth.match(/qop=([a-z]+)/)[1];
    
    // Calculate digest response
    const cnonce = 'test';
    const nc = '00000001';
    const method = 'GET';
    const uri = '/cgi/xml_action.cgi';
    
    const ha1 = crypto.createHash('md5').update(`${USERNAME}:${realm}:${PASSWORD}`).digest('hex');
    const ha2 = crypto.createHash('md5').update(`${method}:${uri}`).digest('hex');
    const responseDigest = crypto.createHash('md5').update(`${ha1}:${nonce}:${nc}:${cnonce}:${qop}:${ha2}`).digest('hex');
    
    const authHeader = `Digest username="${USERNAME}", realm="${realm}", nonce="${nonce}", uri="${uri}", response="${responseDigest}", qop=${qop}, nc=${nc}, cnonce="${cnonce}"`;
    
    instance.defaults.headers.common['Authorization'] = authHeader;
    
    const timestamp = Date.now();
    const [homepage, status] = await Promise.all([
        instance.get(`/xml_action.cgi?method=get&module=duster&file=json_homepage_info${timestamp}`),
        instance.get(`/xml_action.cgi?method=get&module=duster&file=json_status_info${timestamp}`)
    ]);
    
    return formatMifiData(homepage.data, status.data);
}

function formatMifiData(homepage, status) {
    // Convert hex SSID to string
    const ssid = Buffer.from(homepage.ssid, 'hex').toString('utf16be');
    
    // Convert runtime
    const runtimeSeconds = parseInt(status.run_seconds);
    const hours = Math.floor(runtimeSeconds / 3600);
    const minutes = Math.floor((runtimeSeconds % 3600) / 60);
    const seconds = runtimeSeconds % 60;
    
    // Convert bytes to GB
    const sentGB = parseInt(status.tx_byte_all) / (1024 ** 3);
    const receivedGB = parseInt(status.rx_byte_all) / (1024 ** 3);
    
    const sysMode = parseInt(status.sys_mode);
    const networkMode = sysMode === 17 ? '4G' : 'Unknown';
    
    return {
        signal_strength: `${status.rssi} dBm`,
        signal_quality: parseInt(status.signal_quality),
        network_mode: networkMode,
        operator: homepage.network_name,
        connected_devices: parseInt(status.wifi_clients_num),
        runtime: `${hours}h ${minutes}m ${seconds}s`,
        battery_percent: parseInt(status.battery_percent),
        battery_charging: parseInt(status.battery_charging) === 1,
        sent_data: `${sentGB.toFixed(3)}GB`,
        received_data: `${receivedGB.toFixed(3)}GB`,
        current_upload_speed: `${status.tx_speed} KB/s`,
        current_download_speed: `${status.rx_speed} KB/s`,
        ssid: ssid,
        imei: homepage.imei,
        mac: homepage.mac,
        phone_number: homepage.msisdn,
        software_version: homepage.sw_version
    };
}

// Usage
getMifiData().then(data => console.log(JSON.stringify(data, null, 2)));
```

## Response Format Example

```json
{
  "signal_strength": "-70 dBm",
  "signal_quality": 5,
  "network_mode": "4G",
  "operator": "IND TELKOMSEL",
  "connected_devices": 3,
  "runtime": "4h 20m 17s",
  "battery_percent": 90,
  "battery_charging": false,
  "sent_data": "7.041GB",
  "received_data": "87.999GB",
  "current_upload_speed": "0 KB/s",
  "current_download_speed": "0 KB/s",
  "ssid": "kae_mifi",
  "imei": "354144810275881",
  "mac": "e4:7d:eb:a3:8a:17",
  "phone_number": "085156305737",
  "software_version": "LB02ITsel_M0263A_M21_LED_V004"
}
```

## Notes

1. **Timestamp**: The API URLs require a timestamp (milliseconds) appended to the filename. Use the current timestamp for each request.

2. **Polling**: The `json_status_info` endpoint is polled every 2 seconds by the web interface. For real-time monitoring, implement similar polling logic.

3. **SSID Encoding**: The SSID is returned as hex-encoded UTF-16BE string. Convert it before display.

4. **Data Units**: 
   - Data usage is returned in bytes, convert as needed (KB, MB, GB)
   - Speed appears to be in KB/s

5. **Network Modes**: Only `sys_mode = 17` (4G) has been observed. Other values may indicate 3G/2G modes.

6. **Cookie**: The device sets a `nav=0` cookie. Include it in requests if needed.

7. **Session Management**: The digest auth nonce may expire. Re-authenticate if you get 401 responses.
