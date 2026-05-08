export GEMINI_KEY=$(cat /home/ubuntu/.env | grep GEMINI_API_KEY | cut -d'=' -f2)
curl -s "https://generativelanguage.googleapis.com/v1beta/models?key=${GEMINI_KEY}" | jq -r '.models[] | select(.supportedGenerationMethods[] | contains("generateContent")) | "\(.name) - \(.displayName)"'
