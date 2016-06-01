package com.morpheus.jenkins;

import hudson.Launcher;
import hudson.Extension;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.FilePath;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import net.sf.json.JSONObject;
import com.morpheus.sdk.MorpheusClient;
import com.morpheus.sdk.BasicCredentialsProvider;
import com.morpheus.sdk.provisioning.*;
import com.morpheus.sdk.deployment.AppDeploy;
import com.morpheus.sdk.provisioning.Artifact;
import com.morpheus.sdk.provisioning.ArtifactVersion;
import com.morpheus.sdk.provisioning.CreateArtifactRequest;
import com.morpheus.sdk.provisioning.CreateArtifactResponse;
import com.morpheus.sdk.provisioning.CreateArtifactVersionRequest;
import com.morpheus.sdk.provisioning.CreateArtifactVersionResponse;
import com.morpheus.sdk.provisioning.ListArtifactsRequest;
import com.morpheus.sdk.provisioning.ListArtifactsResponse;
import com.morpheus.sdk.deployment.CreateDeployRequest;
import com.morpheus.sdk.deployment.RunDeployRequest;
import com.morpheus.sdk.deployment.RunDeployResponse;
import com.morpheus.sdk.provisioning.UploadFileRequest;
import com.morpheus.sdk.deployment.CreateDeployResponse;

public class MorpheusBuilder extends Builder {
	protected String applianceUrl;
	protected String username;
	protected String password;
	protected String instanceName;
	protected String artifactName;
	protected String workingDirectory;
	protected String includePattern;
	protected String excludePattern;
	protected BasicCredentialsProvider credentialsProvider;

	@DataBoundConstructor
	public MorpheusBuilder(String applianceUrl, String username, String password, String instanceName, String artifactName, String workingDirectory, String includePattern, String excludePattern) {
		this.applianceUrl = applianceUrl;
		this.username = username;
		this.password = password;
		this.instanceName = instanceName;
		this.artifactName = artifactName;
		this.workingDirectory = workingDirectory;
		this.includePattern = includePattern;
		this.excludePattern = excludePattern;
		this.credentialsProvider = new BasicCredentialsProvider(username,password);
	}

    public String getApplianceUrl() {
        return this.applianceUrl;
    }

    public String getUsername() {
        return this.username;
    }

    public String getPassword() {
        return this.password;
    }

    public String getInstanceName() {
        return this.instanceName;
    }

    public String getArtifactName() {
        return this.artifactName;
    }

    public String getIncludePattern() {
        return this.includePattern;
    }

    public String getExcludePattern() {
        return this.excludePattern;
    }

    public String getWorkingDirectory() {
        return this.workingDirectory;
    }


	@Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
    	MorpheusClient client = new MorpheusClient(this.credentialsProvider).setEndpointUrl(this.applianceUrl);
    	AppDeploy appDeploy = new AppDeploy();
        System.out.println("Performing Morpheus Deploy");
    	try {
            // Get or create the artifact specified
            ListArtifactsResponse listArtifactsResponse = client.listArtifacts(new ListArtifactsRequest().name(this.artifactName));
            Long artifactId = null;
            if(listArtifactsResponse.artifacts.size() == 0) {
                Artifact artifact = new Artifact();
                artifact.name = this.artifactName;
                artifact.description = this.artifactName + " - Created by Morpheus Jenkins Plugin";
                CreateArtifactResponse createArtifactResponse = client.createArtifact(new CreateArtifactRequest().artifact(artifact));
                artifactId = createArtifactResponse.artifact.id;
            } else {
                artifactId = listArtifactsResponse.artifacts.get(0).id;
            }

            // Create a new artifact version
            ArtifactVersion artifactVersion = new ArtifactVersion();
            artifactVersion.userVersion = Integer.toString(build.number);
            CreateArtifactVersionResponse createArtifactVersionResponse = client.createArtifactVersion(new CreateArtifactVersionRequest().artifactId(artifactId).artifactVersion(artifactVersion));
            Long artifactVersionId = createArtifactVersionResponse.artifactVersion.id;

            ListInstancesResponse listInstancesResponse = client.listInstances(new ListInstancesRequest().name(this.instanceName));
	    	if(listInstancesResponse.instances != null && listInstancesResponse.instances.size() > 0) {
                // Upload the files
                FilePath rootDir = build.getWorkspace().child(workingDirectory);

                FilePath[] matchedFiles = rootDir.list(includePattern, excludePattern);
                for(int filesCounter = 0; filesCounter < matchedFiles.length; filesCounter++) {
                    FilePath currentFile = matchedFiles[filesCounter];
                    if(!currentFile.isDirectory()) {
                        String destination = rootDir.toURI().relativize(currentFile.getParent().toURI()).getPath();
                        UploadFileRequest fileUploadRequest = new UploadFileRequest().artifactId(artifactId).artifactVersionId(artifactVersionId).inputStream(currentFile.read()).originalName(currentFile.getName()).destination(destination);
                        client.uploadArtifactVersionFile(fileUploadRequest);
                    }
                }

                Long instanceId = listInstancesResponse.instances.get(0).id;
                appDeploy.versionId = artifactVersionId;
                appDeploy.instanceId = instanceId;
                CreateDeployResponse createDeployResponse = client.createDeployment(new CreateDeployRequest().appDeploy(appDeploy));
                Long appDeployId = createDeployResponse.appDeploy.id;
                RunDeployResponse deployResponse = client.runDeploy(new RunDeployRequest().appDeployId(appDeployId));
                return true;
            } else {
               return false;
            }
    	} catch(Exception ex) {
    		System.out.println("Error Occurred During Morpheus Build Phase: " + ex.getMessage());
    		ex.printStackTrace();
    		return false;
    	}
    }

    /**
     * Hudson defines a method {@link Builder#getDescriptor()}, which
     * returns the corresponding {@link BuildStepDescriptor} object.
     *
     * Since we know that it's actually {@link DescriptorImpl}, override
     * the method and give a better return type, so that we can access
     * {@link DescriptorImpl} methods more easily.
     *
     * This is not necessary, but just a coding style preference.
     */
    @Override
    public DescriptorImpl getDescriptor() {
        // see Descriptor javadoc for more about what a descriptor is.
        return (DescriptorImpl)super.getDescriptor();
    }

        /**
     * Descriptor for {@link MorpheusBuilder}.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>views/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    // this annotation tells Hudson that this is the implementation of an extension point
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */

        public DescriptorImpl() {
            load();
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        @Override
        public String getDisplayName() {
            return "Morpheus Deployment";
        }

        /**
         * Applicable to any kind of project.
         */
        @Override
        public boolean isApplicable(Class type) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest staplerRequest, JSONObject json) throws FormException {
            // to persist global configuration information,
            // set that to properties and call save().
            // useFrench = json.getBoolean("useFrench");
            save();
            return true; // indicate that everything is good so far
        }

    }
}
