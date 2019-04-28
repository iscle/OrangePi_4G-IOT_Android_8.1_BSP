/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.databinding

import groovy.io.FileType
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.impl.DefaultServiceLocator
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactDescriptorRequest
import org.eclipse.aether.resolution.ArtifactDescriptorResult
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.tasks.TaskAction

class LocalizeDependenciesTask extends DefaultTask {

    private Set<String> ids = new HashSet<>();

    private Set<String> fetchTestDependencies = new HashSet<>();

    // force download these if they are seen as a dependency
    private Set<String>  wildCard = new HashSet<>();
    {
        wildCard.add("kotlin-gradle-plugin-core")
    }

    List<Artifact> artifactsToResolve = new LinkedList<>();

    Set<String> resolvedArtifacts = new HashSet<>();

    Set<String> failed = new HashSet<>();

    HashMap<String, Object> licenses = new HashMap<>();

    Set<String> missingLicenses = new HashSet<>();

    File localRepoDir;

    @TaskAction
    doIt() {
        println(ids)
        LocalizePluginExtension extension = project.extensions.
                getByName(MavenDependencyCollectorPlugin.EXTENSION_NAME)
        if (extension.localRepoDir == null || extension.otherRepoDirs == null) {

            def msg = "you must configure " +
                    "${MavenDependencyCollectorPlugin.EXTENSION_NAME} with localRepoDir and" +
                    " otherRepoDirs. localRepoDir: " + extension.localRepoDir +
                    "\notherRepoDir:" + extension.otherRepoDirs;
            println(msg)
            println("skipping ${project}")
            return
        }
        localRepoDir = extension.localRepoDir
        downloadAll(extension.localRepoDir, extension.otherRepoDirs)

        if (!missingLicenses.isEmpty()) {
            throw new RuntimeException("Missing licenses for $missingLicenses")
        }
        println("List of new licenses:")
        println(ExportLicensesTask.buildNotice(licenses))
    }

    public void add(MavenDependencyCollectorTask task, ModuleVersionIdentifier id, Configuration conf) {
        def key = toStringIdentifier(id)
        ids.add(key)
        println("adding $key in $conf by $task")
    }

    public static String toStringIdentifier(ModuleVersionIdentifier id) {
        return id.group + ":" + id.name + ":" + id.version;
    }

    private static String artifactKey(Artifact artifact) {
        return artifact.groupId + ":" + artifact.artifactId + ":" + artifact.version;
    }

    public downloadAll(File localRepoDir, List<String> otherRepoDirs) {
        println("downloading all dependencies to $localRepoDir")
        def mavenCentral = new RemoteRepository.Builder("central", "default",
                "http://central.maven.org/maven2/").build();
        def system = newRepositorySystem()
        localRepoDir = localRepoDir.canonicalFile
        List<File> otherRepos = new ArrayList<>()
        otherRepoDirs.each {
            def repo = new File(it).getCanonicalFile()
            if (repo.exists() && !repo.equals(localRepoDir)) {
                otherRepos.add(repo)
            }
        }
        def session = newRepositorySystemSession(system, localRepoDir)
        ids.each {
            def artifact = new DefaultArtifact(it)
            artifactsToResolve.add(artifact)
        }

        while (!artifactsToResolve.isEmpty()) {
            println("remaining artifacts to resolve ${artifactsToResolve.size()}")
            Artifact artifact = artifactsToResolve.remove(0)
            println("    handling artifact ${artifact.getArtifactId()}")
            if (shouldSkip(artifact, otherRepos)) {
                println("skipping $artifact")
                continue
            }
            resolveArtifactWithDependencies(system, session, Arrays.asList(mavenCentral), artifact);
        }
    }

    public static boolean shouldSkip(Artifact artifact, List<File> otherRepos) {
        if (artifact.groupId.startsWith('com.android.databinding') ||
                artifact.groupId.startsWith('com.android.support') ||
                artifact.groupId.equals("jdk")){
            return true
        }
        String targetPath = artifact.groupId.replaceAll("\\.", "/") + "/" + artifact.artifactId +
                "/" + artifact.version
        for (File repo : otherRepos) {
            File f = new File(repo, targetPath)
            if (f.exists()) {
                println("skipping ${artifact} because it exists in $repo")
                return true
            }
        }
        return false
    }

    def boolean isInGit(File file) {
        if (!file.getCanonicalPath().startsWith(localRepoDir.getCanonicalPath())) {
            println("$file is in another git repo, ignore for license")
            return false
        }
        def gitSt = ["git", "status", "--porcelain", file.getCanonicalPath()].
                execute([], localRepoDir)
        gitSt.waitFor()
        if (gitSt.exitValue() != 0) {
            throw new RuntimeException("unable to get git status for $file. ${gitSt.err.text}")
        }
        return gitSt.text.trim().isEmpty()
    }

    public void resolveArtifactWithDependencies(RepositorySystem system,
            RepositorySystemSession session, List<RemoteRepository> remoteRepositories,
            Artifact artifact) {
        def key = artifactKey(artifact)
        if (resolvedArtifacts.contains(key) || failed.contains(key)) {
            return
        }
        resolvedArtifacts.add(key)
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact);
        artifactRequest.setRepositories(remoteRepositories);
        def resolved;
        try {
            resolved = system.resolveArtifact(session, artifactRequest);
        } catch (Throwable ignored) {
            println("cannot find $key, skipping")
            failed.add(key)
            return
        }
        def alreadyInGit = isInGit(resolved.artifact.file)
        println("         |-> resolved ${resolved.artifact.file}. Already in git? $alreadyInGit")



        if (!alreadyInGit) {
            def license = ExportLicensesTask.findLicenseFor(resolved.artifact.artifactId)
            if (license == null) {
                missingLicenses.add(artifactKey(artifact))
            } else {
                licenses.put(resolved.artifact.artifactId, license)
            }
        }

        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(artifact);
        descriptorRequest.setRepositories(remoteRepositories);

        ArtifactDescriptorResult descriptorResult = system.
                readArtifactDescriptor(session, descriptorRequest);
        for (Dependency dependency : descriptorResult.getDependencies()) {
            println("dependency $dependency for $artifact . scope: ${dependency.scope}")
            if ("provided".equals(dependency.scope)) {
                println("skipping $dependency because provided")
                continue
            }
            if ("optional".equals(dependency.scope)) {
                println("skipping $dependency because optional")
                continue
            }
            if ("test".equals(dependency.scope)) {
                if (wildCard.contains(dependency.artifact.getArtifactId()) || fetchTestDependencies.contains(key)) {
                    println("${dependency} is test scope but including because $key is in direct dependencies")
                } else {
                    println("skipping $dependency because test and $key is not first level dependency. artifact id: ${dependency.artifact.getArtifactId()}")
                    continue
                }
            }


            def dependencyKey = artifactKey(dependency.artifact)
            if (resolvedArtifacts.contains(dependencyKey)) {
                println("skipping $dependency because is already resolved as ${dependencyKey}")
                continue
            }
            println("adding to the list ${dependency.artifact}")
            artifactsToResolve.add(dependency.artifact)
        }
        File unwanted = new File(resolved.artifact.file.getParentFile(), "_remote.repositories")
        if (unwanted.exists()) {
            unwanted.delete()
        }
    }

    public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system,
            File localRepoDir) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(localRepoDir);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }

    public static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        return locator.getService(RepositorySystem.class);
    }
}
