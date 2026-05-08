export GEMINI_KEY=$(cat /home/ubuntu/.env | grep GEMINI_API_KEY | cut -d'=' -f2)

# Test gemma-4-31b-it
echo "Testing gemma-4-31b-it..."
curl -s -X POST "https://generativelanguage.googleapis.com/v1beta/models/gemma-4-31b-it:generateContent?key=${GEMINI_KEY}" \
-H 'Content-Type: application/json' \
-d '{"contents":[{"parts":[{"text":"hello"}]}]}'

echo -e "\n\nTesting gemma-4-26b-a4b-it..."
curl -s -X POST "https://generativelanguage.googleapis.com/v1beta/models/gemma-4-26b-a4b-it:generateContent?key=${GEMINI_KEY}" \
-H 'Content-Type: application/json' \
-d '{"contents":[{"parts":[{"text":"hello"}]}]}'

