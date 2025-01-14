/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache Software License 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *******************************************************************************/

package org.eclipse.winery.repository.filebased;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.winery.common.Constants;
import org.eclipse.winery.common.configuration.Environments;
import org.eclipse.winery.repository.backend.IRepository;
import org.eclipse.winery.repository.backend.RepositoryFactory;
import org.eclipse.winery.repository.backend.constants.Filename;
import org.eclipse.winery.repository.backend.filebased.FileUtils;
import org.eclipse.winery.repository.backend.filebased.RepositoryProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiRepositoryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiRepositoryManager.class);

    /**
     * Copies the the list of repositories into the repository root. Reconfigures the factory so that on the next get it
     * will return a MultiRepository
     *
     * @param repositoryList The list of repositories that is copied to the root
     */
    void initializeRepositoryListForMultiRepositoryAndReconfigureFactory(List<RepositoryProperties> repositoryList) {
        ObjectMapper objectMapper = new ObjectMapper();
        File repositoryConfiguration = new File(Environments.getInstance().getRepositoryConfig().getRepositoryRoot(), Filename.FILENAME_JSON_MUTLI_REPOSITORIES);
        if (!repositoryConfiguration.exists()) {
            try {
                Files.createFile(repositoryConfiguration.toPath());
                objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
                objectMapper.writeValue(repositoryConfiguration, repositoryList);
            } catch (IOException exception) {
                LOGGER.error("Failed to create repository folder {}.", repositoryConfiguration.getPath(), exception);
            }
            createMultiRepositoryFileStructure(Paths.get(Environments.getInstance().getRepositoryConfig().getRepositoryRoot()),
                Paths.get(Environments.getInstance().getRepositoryConfig().getRepositoryRoot(), Constants.DEFAULT_LOCAL_REPO_NAME));
        }
        try {
            RepositoryFactory.reconfigure();
        } catch (Exception e) {
            LOGGER.debug("Error reconfiguring the RepositoryFactory.", e);
        }
    }

    boolean isMultiRepositoryFileStructureEstablished(Path repoPath) {
        return FileUtils.isPresent(repoPath.resolve(Constants.DEFAULT_LOCAL_REPO_NAME));
    }

    /**
     * Creates the filestructure for a multirepository and moves the files in localRepository to workspace
     *
     * @param oldPath The path of the old repository
     * @param newPath The path of the workspace folder to which the files are moved
     */
    void createMultiRepositoryFileStructure(Path oldPath, Path newPath) {
        List<String> ignoreFiles = new ArrayList<>();
        if (!Files.exists(newPath)) {
            try {
                Files.createDirectory(newPath);
            } catch (IOException e) {
                LOGGER.error("Failed to create directory {}", newPath, e);
            }
        }
        ignoreFiles.add(Constants.DEFAULT_LOCAL_REPO_NAME);
        ignoreFiles.add(Filename.FILENAME_JSON_MUTLI_REPOSITORIES);
        FileUtils.copyFiles(oldPath, newPath, ignoreFiles);
        try {
            FileUtils.forceDeleteFile(oldPath.resolve(".git").toFile());
        } catch (Exception e) {
            LOGGER.error("Failed to delete .git directory in root folder of the MultiRepository");
        }
        FileUtils.deleteFiles(oldPath, ignoreFiles);
    }

    /**
     * If the repository is a MultiRepository, this method will return the list of all repositories in the
     * MultiRepository. If it is not a MultiRepository, the returned list is empty. This method will also clone the
     * repositories specified in the repositories.json file and their dependencies.
     *
     * @return a list of repositories, represented by their properties.
     */
    public List<RepositoryProperties> getRepositoriesAsList() {
        IRepository repository = RepositoryFactory.getRepository();
        if (repository instanceof MultiRepository) {
            return ((MultiRepository) repository).getRepositoriesFromFile();
        }
        return Collections.emptyList();
    }

    /**
     * If the repository is a MultiRepository, this method will copy the repositories into the repository. If the
     * repository is not a MultiRepository, it will be transformed into one first.
     *
     * @param repositories the list of repositories copied into the MultiRepository
     */
    public void addRepositoryToFile(List<RepositoryProperties> repositories) {
        IRepository repo = RepositoryFactory.getRepository();
        if (!(repo instanceof MultiRepository)) {
            initializeRepositoryListForMultiRepositoryAndReconfigureFactory(repositories);
            repo = RepositoryFactory.getRepository();
        }
        ((MultiRepository) repo).addRepositoryToFile(repositories);
    }
}
