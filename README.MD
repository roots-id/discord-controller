## Install OpenApiGenerator

npm install @openapitools/openapi-generator-cli -g

## Generate Kotlin Client

```bash
openapi-generator-cli generate -i https://bg65j.atalaprism.io/docs/prism-agent/api/openapi-spec.yaml -g kotlin -o c:\Kotlin_API --additional-properties=serializationLibrary=gson
```

Follow the instructions on the OpenApiGenerator generated README.md file to build the generated client.