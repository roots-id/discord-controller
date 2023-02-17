# Discord Controller

This project works together with [WAL-Bot](https://github.com/roots-id/wal-bot) to implement the Discord Social Credential use case.

The purpose of the project is to demonstrate how decentralized identity can be used to create verifiable credentials. Essentially, this means that we can take the metadata associated with your Discord account (such as your username, avatar, and any other information you have provided) and create a digital certificate that can be used to verify your identity online.

Please note that this verifiable credential does not have any practical utility at this time. It is simply an example of how decentralized identity can be used in practice. However, we hope that by showcasing the capabilities of decentralized identity, we can encourage more widespread adoption of this technology and its potential applications.

The credential format provided is a W3C JWT formatted credential.

## Environment variables

### Linux:

- Open the `~/.bashrc` file by typing `nano ~/.bashrc`.
- Add the following lines at the end of the file:
    ```bash
    export ISSUER_URL="[insert URL here]"
    export ISSUER_APIKEY="[insert API key here]"
    ```
- Save the changes and close the editor by pressing "Ctrl + X", "Y", and "Enter".
- Restart the terminal or type "source ~/.bashrc" to reload the environment variables.

### Mac:

- Open the `~/.bash-profile` file
- Add the following lines at the end of the file:
    ```bash
    export ISSUER_URL="[insert URL here]"
    export ISSUER_APIKEY="[insert API key here]"
    ```
- Save the changes and close the editor.
- Restart the terminal or type "source ~/.bash-profile" to reload the environment variables.

### Windows:

- Open the Command Prompt.
- Type the following command to set the environment variables:
    ```bash
    setx ISSUER_URL "[insert URL here]"
    setx ISSUER_APIKEY "[insert API key here]"
    ```
- Close and reopen the Command Prompt to reload the environment variables.

## Install OpenApiGenerator
This is an optional step. If you want to generate the PRISM Agent API client code, you need to install the OpenApiGenerator. You can find the installation instructions [here](https://openapi-generator.tech/docs/installation/)


### Generate Kotlin Client

```bash
openapi-generator-cli generate -i https://[PRISM Agent instance]/docs/prism-agent/api/openapi-spec.yaml -g kotlin -o c:\Kotlin_API --additional-properties=serializationLibrary=gson
```

Follow the instructions on the OpenApiGenerator generated README.md file to build the generated client.
