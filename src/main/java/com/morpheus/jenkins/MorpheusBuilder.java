package com.morpheus.jenkins;

import com.morpheus.sdk.AccessTokenProvider;
import com.morpheus.sdk.BasicCredentialsProvider;
import com.morpheus.sdk.MorpheusClient;
import com.morpheus.sdk.deployment.*;
import com.morpheus.sdk.internal.CredentialsProvider;
import com.morpheus.sdk.provisioning.*;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MorpheusBuilder extends Builder {

    private final static Logger log = LoggerFactory.getLogger(MorpheusBuilder.class);

    protected String applianceUrl;
	protected String username;
	protected String password;
	protected String accessToken;
	protected String instanceName;
	protected String deploymentName;
	protected String userVersion;
	protected String workingDirectory;
	protected String includePattern;
	protected String excludePattern;
	protected BasicCredentialsProvider basicCredentialsProvider;
	protected AccessTokenProvider accessTokenProvider;
	protected CredentialsProvider credentialsProvider;

	@DataBoundConstructor
	public MorpheusBuilder(String applianceUrl, String username, String password, String accessToken, String instanceName, String deploymentName, String userVersion, String workingDirectory, String includePattern, String excludePattern) {
		this.applianceUrl = applianceUrl;
		this.username = username;
		this.password = password;
		this.accessToken = accessToken;
		this.instanceName = instanceName;
		this.deploymentName = deploymentName;
        this.userVersion = userVersion;
		this.workingDirectory = workingDirectory;
		this.includePattern = includePattern;
		this.excludePattern = excludePattern;
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

    public String getDeploymentName() {
        return this.deploymentName;
    }

    public String getUserVersion() {
        return this.userVersion;
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

    public String getAccessToken() {
        return this.accessToken;
    }

	@Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        log.debug("Performing Morpheus Client authentication this.applianceUrl :: {}", this.applianceUrl);
        if(this.accessToken != null && !this.accessToken.trim().isEmpty()) {
            this.credentialsProvider = new AccessTokenProvider(this.accessToken);
        } else {
            this.credentialsProvider = new BasicCredentialsProvider(this.username, this.password);
        }
    	MorpheusClient client = new MorpheusClient(this.credentialsProvider).setEndpointUrl(this.applianceUrl).setSocketTimeout(172800);
    	AppDeploy appDeploy = new AppDeploy();
        log.info("Performing Morpheus Deploy for {}", this.deploymentName);
    	try {
            // Get or create the deployment specified
            ListDeploymentsResponse listDeploymentsResponse = client.listDeployments(new ListDeploymentsRequest().name(this.deploymentName));
            Long deploymentId = null;
            if(listDeploymentsResponse.deployments.size() == 0) {
                Deployment deployment = new Deployment();
                deployment.name = this.deploymentName;
                deployment.description = this.deploymentName + " - Created by Morpheus Jenkins Plugin";
                CreateDeploymentResponse createDeploymentResponse = client.createDeployment(new CreateDeploymentRequest().deployment(deployment));
                deploymentId = createDeploymentResponse.deployment.id;
            } else {
                Deployment deployment = listDeploymentsResponse.deployments.get(0);
                log.info("deployment :: {}", deployment);
                deploymentId = deployment.id;
            }

            // Create a new deployment version
            DeploymentVersion deploymentVersion = new DeploymentVersion();
            log.info("this.userVersion :: {}", this.userVersion);
            EnvVars variables = build.getEnvironment(listener);
            String deployVersion = Integer.toString(build.number);
            String gitCommit = variables.get("GIT_COMMIT");
            if(this.userVersion != null && !this.userVersion.trim().isEmpty()) {
                deployVersion = this.userVersion;
                if(this.userVersion.startsWith("${") && this.userVersion.endsWith("}")) {
                    Pattern parameterEntry = Pattern.compile("\\$\\{(.*?)\\}");
                    Matcher matchPattern = parameterEntry.matcher(this.userVersion);
                    while(matchPattern.find()) {
                        log.info("matchPattern.group(1) :: {}", matchPattern.group(1));
                        if(variables.containsKey(matchPattern.group(1))) {
                            deployVersion = variables.get(matchPattern.group(1));
                        }
                    }
                }
            } else if(gitCommit != null && !gitCommit.trim().isEmpty()) {
                log.info("gitCommit :: {}", gitCommit);
                deployVersion = gitCommit;
            }
            deploymentVersion.userVersion = deployVersion;
            log.info("deploymentVersion.userVersion :: {}", deploymentVersion.userVersion);

            CreateDeploymentVersionResponse createDeploymentVersionResponse = client.createDeploymentVersion(new CreateDeploymentVersionRequest().deploymentId(deploymentId).deploymentVersion(deploymentVersion));
            Long deploymentVersionId = createDeploymentVersionResponse.deploymentVersion.id;

            // Upload the files
            FilePath rootDir = build.getWorkspace().child(workingDirectory);

            FilePath[] matchedFiles = rootDir.list(includePattern, excludePattern);
            for(int filesCounter = 0; filesCounter < matchedFiles.length; filesCounter++) {
                FilePath currentFile = matchedFiles[filesCounter];
                if(!currentFile.isDirectory()) {
                    String destination = rootDir.toURI().relativize(currentFile.getParent().toURI()).getPath();
                    UploadFileRequest fileUploadRequest = new UploadFileRequest().deploymentId(deploymentId).deploymentVersionId(deploymentVersionId).inputStream(currentFile.read()).originalName(currentFile.getName()).destination(destination);
                    client.uploadDeploymentVersionFile(fileUploadRequest);
                }
            }

            log.info("this.instanceName :: {}", this.instanceName);
            if(this.instanceName != null && !this.instanceName.trim().isEmpty()) {
                ListInstancesResponse listInstancesResponse = client.listInstances(new ListInstancesRequest().name(this.instanceName));
                log.info("listInstancesResponse.instances :: {}", listInstancesResponse.instances);
                if(listInstancesResponse.instances != null && listInstancesResponse.instances.size() > 0) {
                    log.info("listInstancesResponse.instances.size() :: {}", listInstancesResponse.instances.size());
                    Long instanceId = listInstancesResponse.instances.get(0).id;
                    log.info("listInstancesResponse.instances.get(0) :: {}", listInstancesResponse.instances.get(0));
                    log.info("instanceId :: {}", instanceId);
                    appDeploy.versionId = deploymentVersionId;
                    appDeploy.instanceId = instanceId;
                    log.info("appDeploy :: {}", appDeploy);

                    CreateDeployResponse createDeployResponse = client.createDeployment(new CreateDeployRequest().appDeploy(appDeploy));
                    log.info("createDeployResponse :: {}", createDeployResponse);
                    Long appDeployId = createDeployResponse.appDeploy.id;
                    if(createDeployResponse.appDeploy.status == "staged") {
                        RunDeployResponse deployResponse = client.runDeploy(new RunDeployRequest().appDeployId(appDeployId));
                        log.info("deployResponse :: {}", deployResponse);
                    }
                    return true;
                } else {
                    return false;
                }
            } else {
                log.info("this.instanceName was not set. Ignoring rest of the deployment");
                return true;
            }
    	} catch(Exception ex) {
    		log.error("Error Occurred During Morpheus Build Phase :: {}", ex.getMessage());
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

    private void printOutEnvVariables(AbstractBuild build, BuildListener listener) {
        try {
            EnvVars variables = build.getEnvironment(listener);
            Iterator<String> itr = variables.keySet().iterator();

            while (itr.hasNext())
            {
                Object key = itr.next();
                Object value = variables.get(key);

                log.info("{} = {}", key, value);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
