# Morpheus Jenkins

The Morpheus Jenkins plugin provides tasks for deployment automation to the Morpheus Cloud Management platform. Deploy any of your applications to a morpheus on or off-premise setup.

## Installation

Currently, the only way to install this plugin is manually.

1. Clone this repository and generate the binary by running the gradle task assemble.
   Example: `gradle assemble` or if you use gradle wrapper `gradlew assemble`
2. You will find the binary file at this location
   
   `build/libs/morpheus-deployment.hpi`
3. Now go to your Jenkins server and log in with Admin privileges.
4. Then navigate to `Jenkins > Manage Jenkins > Manage Plugins` and select the `Advanced` tab.
5. You will find an option to upload the morpheus-deployment.hpi file.
6. Restart your Jenkins server.

## Usage

### Prequisites
- Morpheus API Access. For more information, [click here](https://docs.morpheusdata.com/en/latest/administration/user_settings/user_settings.html?highlight=api%20access#api-access)

### Configuration
- If your project has multiple deployments, you can add multiple instances of this build step.

#### *Table 1. Build Step configuration options*
| Property | Required | Meaning | Example |
|----------|----------|---------------|---------|
| Appliance URL | yes | The URL to your Morpheus appliance. |  |
| Access Token | yes |  
| Deployment Name | yes | Identify the Morpheus Deployment to use. Build artifacts will be uploaded as a new version under this Deployment. If the Deployment does not exist in Morpheus, a new Deployment will be created. | |
| Deployment Version | no | Identify the Morpheus Deployment Version. If the Deployment Version is not defined then the Jenkins build number will be used instead. If this field is left blank the GIT_COMMIT value will be used. | |
| Instance Name | no | Identify which Morpheus Instance should be updated with the build artifacts. If this field is left blank then no deployment will be attempted. | |
| Working Directory | no | | build/libs/ |
| Include Patterns | no | | *.war |
| Exclude Patterns | no | | *.log |


## Resources

* [Java APIDoc](http://gomorpheus.github.io/morpheus-java-sdk)
* [Web APIDoc](http://bertramdev.github.io/morpheus-apidoc/)
* [Morpheus Website](https://www.gomorpheus.com)
