#!/bin/bash

echo "=== Testing LinkAPI Chat Completions API ==="
echo "API URL: https://api.linkapi.ai/v1/chat/completions"
echo "Model: gpt-5.4-mini"
echo ""

response=$(curl -X POST https://api.linkapi.ai/v1/chat/completions \
  -H "Content-Type: application/json; charset=utf-8" \
  -H "Authorization: Bearer sk-s8CgcDvsftJQb50MT7BMPdU22MwCJFcj397zWwGetAMiqleR" \
  -d '{
    "model": "gpt-5.4-mini",
    "messages": [
      {"role": "system", "content": "You are a helpful assistant."},
      {"role": "user", "content": "Hello, please respond with just OK if you can read this."}
    ]
  }' \
  --connect-timeout 10 \
  --max-time 30 \
  -w "\n\nHTTP_CODE: %{http_code}\n" \
  -v 2>&1)

echo "$response"

# Check HTTP status code
if echo "$response" | grep -q "HTTP_CODE: 200"; then
    echo ""
    echo "✅ LinkAPI Chat Completions API Connection SUCCESSFUL!"
    exit 0
else
    echo ""
    echo "❌ LinkAPI Chat Completions API Connection FAILED!"
    echo "Check the error message above for details."
    exit 1
fi
