/*******************************************************************************
 * Copyright (c) 2020, 2025 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.core.resolver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.equinox.internal.p2.publisher.eclipse.FeatureParser;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.VersionedId;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.actions.IPropertyAdvice;
import org.eclipse.equinox.p2.publisher.eclipse.Feature;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.m2e.pde.target.shared.AdditionalRepository;
import org.eclipse.m2e.pde.target.shared.DependencyResult;
import org.eclipse.m2e.pde.target.shared.MavenBundleWrapper;
import org.eclipse.m2e.pde.target.shared.MavenDependencyCollector;
import org.eclipse.m2e.pde.target.shared.MavenRootDependency;
import org.eclipse.m2e.pde.target.shared.ProcessingMessage;
import org.eclipse.m2e.pde.target.shared.WrappedBundle;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.tycho.IArtifactFacade;
import org.eclipse.tycho.TychoConstants;
import org.eclipse.tycho.core.DependencyResolutionException;
import org.eclipse.tycho.core.MavenDependenciesResolver;
import org.eclipse.tycho.core.MavenModelFacade;
import org.eclipse.tycho.core.maven.AetherArtifactFacade;
import org.eclipse.tycho.core.publisher.TychoMavenPropertiesAdvice;
import org.eclipse.tycho.core.resolver.shared.IncludeSourceMode;
import org.eclipse.tycho.core.resolver.target.FileArtifactRepository;
import org.eclipse.tycho.core.resolver.target.SupplierMetadataRepository;
import org.eclipse.tycho.core.shared.MavenContext;
import org.eclipse.tycho.core.shared.MavenLogger;
import org.eclipse.tycho.p2.repository.GAV;
import org.eclipse.tycho.p2.resolver.BundlePublisher;
import org.eclipse.tycho.p2.resolver.FeatureGenerator;
import org.eclipse.tycho.p2.resolver.FeaturePublisher;
import org.eclipse.tycho.p2.resolver.WrappedArtifact;
import org.eclipse.tycho.p2maven.advices.MavenChecksumAdvice;
import org.eclipse.tycho.p2maven.advices.MavenPropertiesAdvice;
import org.eclipse.tycho.p2maven.tmp.BundlesAction;
import org.eclipse.tycho.targetplatform.TargetDefinition.BNDInstructions;
import org.eclipse.tycho.targetplatform.TargetDefinition.MavenDependency;
import org.eclipse.tycho.targetplatform.TargetDefinition.MavenGAVLocation;
import org.eclipse.tycho.targetplatform.TargetDefinition.MavenGAVLocation.DependencyDepth;
import org.eclipse.tycho.targetplatform.TargetDefinition.MavenGAVLocation.MissingManifestStrategy;
import org.eclipse.tycho.targetplatform.TargetDefinitionContent;
import org.eclipse.tycho.targetplatform.TargetDefinitionResolutionException;
import org.osgi.framework.BundleException;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * This implements the "maven" target location type
 */
public class MavenTargetDefinitionContent implements TargetDefinitionContent {

    private static final RemoteRepository CENTRAL = new RemoteRepository.Builder(
            RepositorySystem.DEFAULT_REMOTE_REPO_ID, "default", RepositorySystem.DEFAULT_REMOTE_REPO_URL)
                    .setReleasePolicy(new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_NEVER,
                            RepositoryPolicy.CHECKSUM_POLICY_WARN))
                    .setSnapshotPolicy(new RepositoryPolicy(false, RepositoryPolicy.UPDATE_POLICY_NEVER,
                            RepositoryPolicy.CHECKSUM_POLICY_WARN))
                    .build();

    private static final String POM_PACKAGING_TYPE = "pom";
    private final Map<IArtifactDescriptor, IInstallableUnit> repositoryContent = new HashMap<>();
    private SupplierMetadataRepository metadataRepository;
    private FileArtifactRepository artifactRepository;
    private MavenContext mavenContext;

    MavenTargetDefinitionContent(MavenGAVLocation location, MavenDependenciesResolver mavenDependenciesResolver,
            IncludeSourceMode sourceMode, IProvisioningAgent agent, MavenContext mavenContext,
            SyncContextFactory syncContextFactory, RepositorySystem repositorySystem, MavenSession mavenSession,
            org.eclipse.aether.RepositorySystem repositorySystem2) {
        this.mavenContext = mavenContext;
        MavenLogger logger = mavenContext.getLogger();
        File repositoryRoot = mavenDependenciesResolver.getRepositoryRoot();
        boolean includeSource = sourceMode == IncludeSourceMode.force
                || (sourceMode == IncludeSourceMode.honor && location.includeSource());
        metadataRepository = new SupplierMetadataRepository(agent, () -> repositoryContent.values().iterator());
        metadataRepository.setLocation(repositoryRoot.toURI());
        metadataRepository.setName(repositoryRoot.getName());
        artifactRepository = new FileArtifactRepository(agent, () -> repositoryContent.keySet().stream()
                .filter(Predicate.not(FeaturePublisher::isMetadataOnly)).iterator());
        artifactRepository.setName(repositoryRoot.getName());
        artifactRepository.setLocation(repositoryRoot.toURI());
        Collection<BNDInstructions> instructions = location.getInstructions();
        List<Feature> features = new ArrayList<>();
        logger.info("Resolving " + location);
        Map<String, Properties> instructionsMap = new HashMap<>();
        for (BNDInstructions instruction : instructions) {
            String reference = instruction.getReference();
            Properties properties = instruction.getInstructions();
            instructionsMap.put(reference, properties);
            logger.info((reference.isEmpty() ? "default instructions" : reference) + " = " + properties);
        }
        List<AdditionalRepository> references = location.getRepositoryReferences().stream()
                .map(rr -> new AdditionalRepository(rr.getId(), rr.getUrl())).toList();
        MavenDependencyCollector collector = new MavenDependencyCollector(repositorySystem2,
                mavenSession.getRepositorySession(), getRepos(mavenSession), references,
                convert(location.getIncludeDependencyDepth()), location.getIncludeDependencyScopes());
        List<IInstallableUnit> locationBundles = new ArrayList<>();
        List<IInstallableUnit> locationSourceBundles = new ArrayList<>();
        for (MavenDependency mavenDependency : location.getRoots()) {
            ResolvedMavenArtifacts resolve;
            try {
                resolve = resolveRoot(collector, mavenDependency);
            } catch (RepositoryException re) {
                throw new TargetDefinitionResolutionException(
                        "MavenDependency " + mavenDependency + " of location " + location + " could not be resolved",
                        re);
            }
            Iterator<IArtifactFacade> resolvedArtifacts = resolve.facades().stream()
                    .filter(IArtifactFacade.class::isInstance).map(IArtifactFacade.class::cast).iterator();
            Properties defaultProperties = WrappedArtifact.createPropertiesForPrefix("wrapped");
            List<IInstallableUnit> bundles = new ArrayList<>();
            List<IInstallableUnit> sourceBundles = new ArrayList<>();
            while (resolvedArtifacts.hasNext()) {
                IArtifactFacade mavenArtifact = resolvedArtifacts.next();
                if (mavenDependency.isIgnored(mavenArtifact)) {
                    logger.debug("Skip ignored " + mavenArtifact);
                    continue;
                }
                if (POM_PACKAGING_TYPE.equalsIgnoreCase(mavenArtifact.getPackagingType())) {
                    logger.debug("Skip pom artifact " + mavenArtifact);
                    continue;
                }
                String fileName = mavenArtifact.getLocation().getName();
                if (!"jar".equalsIgnoreCase(FilenameUtils.getExtension(fileName))) {
                    logger.info("Skip non-jar artifact (" + fileName + ")");
                    continue;
                }
                logger.debug("Resolved " + mavenArtifact);

                Feature feature = new FeatureParser().parse(mavenArtifact.getLocation());
                if (feature != null) {
                    feature.setLocation(mavenArtifact.getLocation().getAbsolutePath());
                    features.add(feature);
                    continue;
                }

                String symbolicName;
                String bundleVersion;
                String debugString = asDebugString(mavenArtifact);
                try {
                    File bundleLocation = mavenArtifact.getLocation();
                    BundleDescription bundleDescription = BundlesAction.createBundleDescription(bundleLocation);
                    symbolicName = bundleDescription != null ? bundleDescription.getSymbolicName() : null;
                    bundleVersion = bundleDescription != null ? bundleDescription.getVersion().toString() : null;
                    IInstallableUnit unit;
                    if (symbolicName == null) {
                        if (location.getMissingManifestStrategy() == MissingManifestStrategy.IGNORE) {
                            logger.info("Ignoring " + debugString
                                    + " as it is not a bundle and MissingManifestStrategy is set to ignore for this location");
                            continue;
                        }
                        if (location.getMissingManifestStrategy() == MissingManifestStrategy.ERROR) {
                            throw new TargetDefinitionResolutionException("Artifact " + debugString
                                    + " is not a bundle and MissingManifestStrategy is set to error for this location");
                        }
                        try {
                            Function<DependencyNode, Properties> instructionsLookup = node -> instructionsMap
                                    .getOrDefault(getKey(node.getArtifact()),
                                            instructionsMap.getOrDefault("", defaultProperties));
                            WrappedBundle wrappedBundle = MavenBundleWrapper.getWrappedArtifact(
                                    new DefaultArtifact(mavenArtifact.getGroupId(), mavenArtifact.getArtifactId(),
                                            mavenArtifact.getClassifier(), mavenArtifact.getPackagingType(),
                                            mavenArtifact.getVersion()),
                                    instructionsLookup, collector.getEffectiveRepositories(), repositorySystem2,
                                    mavenSession.getRepositorySession(), syncContextFactory);
                            List<ProcessingMessage> directErrors = wrappedBundle.messages(false)
                                    .filter(msg -> msg.type() == ProcessingMessage.Type.ERROR).toList();
                            if (directErrors.isEmpty()) {
                                wrappedBundle.messages(true).map(ProcessingMessage::message)
                                        .forEach(msg -> logger.warn(debugString + ": " + msg));
                            } else {
                                String error = directErrors.stream().map(ProcessingMessage::message)
                                        .collect(Collectors.joining(System.lineSeparator()));
                                String hint = String.format(
                                        "You can exclude it by adding <exclude>%s</exclude> to your location",
                                        debugString);
                                throw new RuntimeException(
                                        String.format("Dependency %s of %s can not be wrapped: %s%s%s", debugString,
                                                mavenDependency, error, System.lineSeparator().repeat(2), hint));
                            }
                            File file = wrappedBundle.getFile().get().toFile();
                            BundleDescription description = BundlesAction.createBundleDescription(file);
                            WrappedArtifact wrappedArtifact = new WrappedArtifact(file, mavenArtifact,
                                    mavenArtifact.getClassifier(), description.getSymbolicName(),
                                    description.getVersion().toString(), null);
                            logger.info(debugString + " is wrapped as a bundle with bundle symbolic name "
                                    + wrappedArtifact.getWrappedBsn());
                            logger.info(wrappedArtifact.getReferenceHint());
                            if (logger.isDebugEnabled()) {
                                logger.debug("The following manifest was generated for this artifact:\r\n"
                                        + wrappedArtifact.getGeneratedManifest());
                            }
                            // Maven artifact info for wrapped bundles have to be stored in separate fields
                            Map<String, String> mavenProperties = new HashMap<>();
                            mavenProperties.put(TychoConstants.PROP_WRAPPED_GROUP_ID, mavenArtifact.getGroupId());
                            mavenProperties.put(TychoConstants.PROP_WRAPPED_ARTIFACT_ID, mavenArtifact.getArtifactId());
                            mavenProperties.put(TychoConstants.PROP_WRAPPED_VERSION, mavenArtifact.getVersion());
                            mavenProperties.put(TychoConstants.PROP_WRAPPED_CLASSIFIER, mavenArtifact.getClassifier());
                            unit = publish(description, file, new MavenPropertiesAdvice(mavenProperties));
                            symbolicName = wrappedArtifact.getWrappedBsn();
                            bundleVersion = wrappedArtifact.getWrappedVersion();
                        } catch (Exception e) {
                            throw new TargetDefinitionResolutionException("Artifact " + debugString + " of location "
                                    + location + " could not be wrapped as a bundle", e);
                        }

                    } else {
                        unit = publish(bundleDescription, bundleLocation, mavenArtifact);
                    }
                    bundles.add(unit);
                    if (logger.isDebugEnabled()) {
                        logger.debug("MavenResolver: artifact " + debugString + " at location " + bundleLocation
                                + " resolves installable unit " + new VersionedId(unit.getId(), unit.getVersion()));
                    }
                } catch (BundleException | IOException e) {
                    throw new TargetDefinitionResolutionException(
                            "Artifact " + debugString + " of location " + location + " could not be read", e);
                }

                if (includeSource) {
                    try {
                        Collection<?> sourceArtifacts = mavenDependenciesResolver.resolve(mavenArtifact.getGroupId(),
                                mavenArtifact.getArtifactId(), mavenArtifact.getVersion(),
                                mavenArtifact.getPackagingType(), "sources", null,
                                MavenDependenciesResolver.DEEP_NO_DEPENDENCIES, location.getRepositoryReferences());
                        Iterator<IArtifactFacade> sources = sourceArtifacts.stream()
                                .filter(IArtifactFacade.class::isInstance).map(IArtifactFacade.class::cast).iterator();
                        while (sources.hasNext()) {
                            IArtifactFacade sourceArtifact = sources.next();
                            File sourceFile = sourceArtifact.getLocation();
                            try {
                                Manifest manifest;
                                try (JarFile jar = new JarFile(sourceFile)) {
                                    manifest = Objects.requireNonNullElseGet(jar.getManifest(), Manifest::new);
                                }
                                IInstallableUnit unit;
                                if (MavenBundleWrapper.isValidSourceManifest(manifest)) {
                                    unit = publish(BundlesAction.createBundleDescription(sourceFile), sourceFile,
                                            sourceArtifact);
                                } else {
                                    unit = generateSourceBundle(symbolicName, bundleVersion, manifest, sourceFile,
                                            sourceArtifact);
                                }
                                sourceBundles.add(unit);
                                if (unit != null && logger.isDebugEnabled()) {
                                    logger.debug("MavenResolver: source-artifact " + asDebugString(sourceArtifact)
                                            + ":sources at location " + sourceFile + " resolves installable unit "
                                            + new VersionedId(unit.getId(), unit.getVersion()));
                                }
                            } catch (IOException | BundleException e) {
                                logger.warn("MavenResolver: source-artifact " + asDebugString(sourceArtifact)
                                        + ":sources at location " + sourceFile
                                        + " cannot be converted to a source bundle: " + e);
                                continue;
                            }
                        }
                    } catch (DependencyResolutionException e) {
                        logger.warn(
                                "MavenResolver: source-artifact " + debugString + ":sources cannot be resolved: " + e);
                    }
                }
            }
            if (POM_PACKAGING_TYPE.equalsIgnoreCase(mavenDependency.getArtifactType())) {
                Optional<File> pomFacade = Optional.ofNullable(resolve.root().getFile());
                if (pomFacade.isPresent()) {
                    try {
                        MavenModelFacade model = mavenDependenciesResolver.loadModel(pomFacade.get());
                        features.add(FeatureGenerator.generatePomFeature(model, bundles, false, logger));
                        if (includeSource) {
                            features.add(FeatureGenerator.generatePomFeature(model, sourceBundles, true, logger));
                        }
                    } catch (IOException | ParserConfigurationException | TransformerException | SAXException e) {
                        throw new TargetDefinitionResolutionException("non readable pom file");
                    }
                }
            }
            locationBundles.addAll(bundles);
            locationSourceBundles.addAll(sourceBundles);
        }
        Element featureTemplate = location.getFeatureTemplate();
        if (featureTemplate != null) {
            try {
                features.add(
                        FeatureGenerator.createFeatureFromTemplate(featureTemplate, locationBundles, false, logger));
                if (includeSource) {
                    features.add(FeatureGenerator.createFeatureFromTemplate(featureTemplate, locationSourceBundles,
                            true, logger));
                }
            } catch (IOException | ParserConfigurationException | TransformerException | SAXException e) {
                throw new TargetDefinitionResolutionException("feature generation failed!", e);
            }
        }
        FeaturePublisher.publishFeatures(features, repositoryContent::put, artifactRepository, logger);
    }

    private List<RemoteRepository> getRepos(MavenSession mavenSession) {
        MavenProject project = mavenSession.getCurrentProject();
        if (project != null) {
            return RepositoryUtils.toRepos(project.getRemoteArtifactRepositories());
        } else {
            Settings settings = mavenSession.getSettings();
            List<String> activeProfiles = settings.getActiveProfiles();
            List<RemoteRepository> fromSetting = settings.getProfiles().stream()
                    .filter(p -> activeProfiles.contains(p.getId())).flatMap(p -> p.getRepositories().stream())
                    .filter(r -> "default".equals(r.getLayout())).map(r -> {
                        return new RemoteRepository.Builder(r.getId(), r.getLayout(), r.getUrl())
                                .setReleasePolicy(toPolicy(r.getReleases()))
                                .setSnapshotPolicy(toPolicy(r.getSnapshots())).build();
                    }).toList();
            if (fromSetting.size() > 0) {
                return fromSetting;
            }
        }
        return List.of(CENTRAL);
    }

    private RepositoryPolicy toPolicy(org.apache.maven.settings.RepositoryPolicy repositoryPolicy) {
        return new RepositoryPolicy(repositoryPolicy.isEnabled(), RepositoryPolicy.UPDATE_POLICY_ALWAYS,
                RepositoryPolicy.CHECKSUM_POLICY_WARN);
    }

    private ResolvedMavenArtifacts resolveRoot(MavenDependencyCollector collector, MavenDependency mavenDependency)
            throws RepositoryException {
        DependencyResult collect = collector.collect(new MavenRootDependency(mavenDependency.getGroupId(),
                mavenDependency.getArtifactId(), mavenDependency.getVersion(), mavenDependency.getClassifier(),
                mavenDependency.getArtifactType()));
        List<AetherArtifactFacade> list = collect.artifacts().stream().filter(a -> a.artifact().getFile() != null)
                .map(a -> new AetherArtifactFacade(a.artifact(), a.repository())).toList();
        return new ResolvedMavenArtifacts(list, collect.root().getArtifact());
    }

    private org.eclipse.m2e.pde.target.shared.DependencyDepth convert(DependencyDepth dependencyDepth) {
        switch (dependencyDepth) {
        case DIRECT:
            return org.eclipse.m2e.pde.target.shared.DependencyDepth.DIRECT;
        case INFINITE:
            return org.eclipse.m2e.pde.target.shared.DependencyDepth.INFINITE;
        default:
            return org.eclipse.m2e.pde.target.shared.DependencyDepth.NONE;
        }
    }

    private IInstallableUnit generateSourceBundle(String symbolicName, String bundleVersion, Manifest manifest,
            File sourceFile, IArtifactFacade sourceArtifact) throws IOException, BundleException {

        File tempFile = File.createTempFile("tycho_wrapped_source", ".jar");
        tempFile.deleteOnExit();
        MavenBundleWrapper.addSourceBundleMetadata(manifest, symbolicName, bundleVersion);
        MavenBundleWrapper.transferJarEntries(sourceFile, manifest, tempFile);
        return publish(BundlesAction.createBundleDescription(tempFile), tempFile, sourceArtifact);

    }

    private IInstallableUnit publish(BundleDescription bundleDescription, File bundleLocation,
            IArtifactFacade mavenArtifact) {
        return publish(bundleDescription, bundleLocation, new TychoMavenPropertiesAdvice(mavenArtifact, mavenContext));
    }

    private IInstallableUnit publish(BundleDescription bundleDescription, File bundleLocation, IPropertyAdvice advice) {
        IArtifactKey key = BundlesAction.createBundleArtifactKey(bundleDescription.getSymbolicName(),
                bundleDescription.getVersion().toString());
        IArtifactDescriptor descriptor = FileArtifactRepository.forFile(bundleLocation, key, artifactRepository);
        PublisherInfo publisherInfo = new PublisherInfo();
        publisherInfo.addAdvice(advice);
        publisherInfo.addAdvice(new MavenChecksumAdvice(bundleLocation));
        publisherInfo.setArtifactOptions(IPublisherInfo.A_INDEX);
        IInstallableUnit iu = BundlePublisher.publishBundle(bundleDescription, descriptor, publisherInfo);
        repositoryContent.put(descriptor, iu);
        return iu;
    }

    private String asDebugString(IArtifactFacade mavenArtifact) {
        return new GAV(mavenArtifact.getGroupId(), mavenArtifact.getArtifactId(), mavenArtifact.getVersion())
                .toString();
    }

    @Override
    public IArtifactRepository getArtifactRepository() {
        return artifactRepository;
    }

    @Override
    public IMetadataRepository getMetadataRepository() {
        return metadataRepository;
    }

    private static String getKey(Artifact artifact) {
        if (artifact == null) {
            return "";
        }
        String key = artifact.getGroupId() + ":" + artifact.getArtifactId();
        String classifier = artifact.getClassifier();
        if (classifier != null) {
            key += ":" + classifier;
        }
        key += ":" + artifact.getVersion();
        return key;
    }

    private static record ResolvedMavenArtifacts(Collection<AetherArtifactFacade> facades, Artifact root) {

    }

}
