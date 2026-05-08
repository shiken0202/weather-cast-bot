import urllib.request
import json
import os

key = os.popen("cat /home/ubuntu/.env | grep CWA_API_KEY | cut -d'=' -f2").read().strip()
url = f"https://opendata.cwa.gov.tw/api/v1/rest/datastore/E-A0015-001?Authorization={key}"
req = urllib.request.Request(url)
with urllib.request.urlopen(req) as response:
    data = json.loads(response.read().decode())
    areas = data['records']['Earthquake'][0]['Intensity']['ShakingArea']
    for a in areas:
        print(a.get('CountyName', ''), a.get('AreaIntensity', ''))
