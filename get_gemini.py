import urllib.request
import json
import os

key = os.popen("cat /home/ubuntu/.env | grep GEMINI_API_KEY | cut -d'=' -f2").read().strip()
if not key:
    with open("src/main/resources/application.yml", "r") as f:
        for line in f:
            if "GEMINI_API_KEY" in line or "apiKey:" in line:
                pass # usually env
print(key)
