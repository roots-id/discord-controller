## Install OpenApiGenerator

npm install @openapitools/openapi-generator-cli -g

## Generate Kotlin Client

openapi-generator-cli generate -i http://localhost:8080/prism-agent/api/openapi-spec.yaml -g kotlin -o c:\OAS\Kotlin_local --additional-properties=serializationLibrary=gson

Follow the instructions on the OpenApiGenerator generated README.md file to build the generated client.