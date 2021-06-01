package ne.zig.plugin;


import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.transfer.artifact.ArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.DependableCoordinate;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Resolves a single artifact. Heavily borrowed from maven dependency plugin
 *
 */
@Component(
        role = ArtifactDownloader.class,
        hint = "default"
)
public class ArtifactDownloader {

    @Requirement
    private ArtifactResolver artifactResolver;

    @Requirement
    private ArtifactHandlerManager artifactHandlerManager;

    @Requirement
    private RepositorySystem repositorySystem;

    private final DefaultDependableCoordinate coordinate = new DefaultDependableCoordinate();


    public ArtifactResult getArtifact(MavenSession session, List<ArtifactRepository> pomRemoteRepositories, String artifact)
            throws ArtifactResolverException {

        Objects.requireNonNull(artifact);

        String[] tokens = StringUtils.split(artifact, ":");
        if (tokens.length < 3 || tokens.length > 5) {
            throw new IllegalArgumentException("Invalid artifact, you must specify "
                    + "groupId:artifactId:version[:packaging[:classifier]] " + artifact);
        }

        coordinate.setGroupId(tokens[0]);
        coordinate.setArtifactId(tokens[1]);
        coordinate.setVersion(tokens[2]);
        if (tokens.length >= 4) {
            coordinate.setType(tokens[3]);
        }

        if (tokens.length == 5) {
            coordinate.setClassifier(tokens[4]);
        }

        List<ArtifactRepository> repoList = new ArrayList<>();

        if (pomRemoteRepositories != null) {
            repoList.addAll(pomRemoteRepositories);
        }

        ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

        Settings settings = session.getSettings();
        repositorySystem.injectMirror(repoList, settings.getMirrors());
        repositorySystem.injectProxy(repoList, settings.getProxies());
        repositorySystem.injectAuthentication(repoList, settings.getServers());

        buildingRequest.setRemoteRepositories(repoList);

        return artifactResolver.resolveArtifact(buildingRequest, toArtifactCoordinate(coordinate));
    }

    private ArtifactCoordinate toArtifactCoordinate(DependableCoordinate dependableCoordinate) {
        ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler(dependableCoordinate.getType());
        DefaultArtifactCoordinate artifactCoordinate = new DefaultArtifactCoordinate();
        artifactCoordinate.setGroupId(dependableCoordinate.getGroupId());
        artifactCoordinate.setArtifactId(dependableCoordinate.getArtifactId());
        artifactCoordinate.setVersion(dependableCoordinate.getVersion());
        artifactCoordinate.setClassifier(dependableCoordinate.getClassifier());
        artifactCoordinate.setExtension(artifactHandler.getExtension());
        return artifactCoordinate;
    }

}