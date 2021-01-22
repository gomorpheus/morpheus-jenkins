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

[FILL THIS IN]

## Resources

* [Java APIDoc](http://gomorpheus.github.io/morpheus-java-sdk)
* [Web APIDoc](http://bertramdev.github.io/morpheus-apidoc/)
* [Morpheus Website](https://www.gomorpheus.com)
