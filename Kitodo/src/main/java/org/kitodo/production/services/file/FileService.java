/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.production.services.file;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.command.CommandResult;
import org.kitodo.api.filemanagement.FileManagementInterface;
import org.kitodo.api.filemanagement.LockResult;
import org.kitodo.api.filemanagement.LockingMode;
import org.kitodo.api.filemanagement.ProcessSubType;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.data.database.beans.Folder;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Ruleset;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.helper.enums.MetadataFormat;
import org.kitodo.production.file.BackupFileRotation;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.metadata.ImageHelper;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyMetsModsDigitalDocumentHelper;
import org.kitodo.production.model.Subfolder;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.command.CommandService;
import org.kitodo.production.services.data.RulesetService;
import org.kitodo.production.services.data.UserService;
import org.kitodo.serviceloader.KitodoServiceLoader;

public class FileService {

    private static final String SYSTEM_LOCKING_USER = "System";
    private static final Logger logger = LogManager.getLogger(FileService.class);
    private static final String TEMPORARY_FILENAME_PREFIX = "temporary_";

    private volatile FileManagementInterface fileManagementModule = new KitodoServiceLoader<FileManagementInterface>(
            FileManagementInterface.class).loadModule();

    /**
     * Creates a MetaDirectory.
     *
     * @param parentFolderUri
     *            The URI, where the
     * @param directoryName
     *            the name of the directory
     * @return true or false
     * @throws IOException
     *             an IOException
     */
    URI createMetaDirectory(URI parentFolderUri, String directoryName) throws IOException {
        if (!fileExist(parentFolderUri.resolve(directoryName))) {
            CommandService commandService = ServiceManager.getCommandService();
            String path = FileSystems.getDefault()
                    .getPath(ConfigCore.getKitodoDataDirectory(), parentFolderUri.getRawPath(), directoryName)
                    .normalize().toAbsolutePath().toString();
            List<String> commandParameter = Collections.singletonList(path);
            File script = new File(ConfigCore.getParameter(ParameterCore.SCRIPT_CREATE_DIR_META));
            CommandResult commandResult = commandService.runCommand(script, commandParameter);
            if (!commandResult.isSuccessful()) {
                String message = MessageFormat.format(
                    "Could not create directory {0} in {1}! No new directory was created", directoryName,
                    parentFolderUri.getPath());
                logger.warn(message);
                throw new IOException(message);
            }
        } else {
            logger.info("Metadata directory: " + directoryName + " already existed! No new directory was created");
        }
        return URI.create(parentFolderUri.getPath() + '/' + directoryName);
    }

    /**
     * Creates a directory at a given URI with a given name.
     *
     * @param parentFolderUri
     *            the uri, where the directory should be created
     * @param directoryName
     *            the name of the directory.
     * @return the URI of the new directory or URI of parent directory if
     *         directoryName is null or empty
     */
    public URI createDirectory(URI parentFolderUri, String directoryName) throws IOException {
        if (Objects.nonNull(directoryName)) {
            return fileManagementModule.create(parentFolderUri, directoryName, false);
        }
        return URI.create("");
    }

    /**
     * Creates a directory with a name given and assigns permissions to the
     * given user. Under Linux a script is used to set the file system
     * permissions accordingly. This cannot be done from within java code before
     * version 1.7.
     *
     * @param dirName
     *            Name of directory to create
     * @throws IOException
     *             If an I/O error occurs.
     */
    public void createDirectoryForUser(URI dirName, String userName) throws IOException {
        if (!fileExist(dirName)) {
            CommandService commandService = ServiceManager.getCommandService();
            List<String> commandParameter = Arrays.asList(userName, new File(dirName).getAbsolutePath());
            commandService.runCommand(new File(ConfigCore.getParameter(ParameterCore.SCRIPT_CREATE_DIR_USER_HOME)),
                commandParameter);
        }
    }

    /**
     * Creates the folder structure needed for a process.
     *
     * @param process
     *            the process
     * @return the URI to the process location
     */
    public URI createProcessLocation(Process process) throws IOException {
        URI processLocationUri = fileManagementModule.createProcessLocation(process.getId().toString());
        for (Folder folder : process.getProject().getFolders()) {
            if (folder.isCreateFolder()) {
                URI parentFolderUri = processLocationUri;
                for (String singleFolder : new Subfolder(process, folder).getRelativeDirectoryPath()
                        .split(Pattern.quote(File.separator))) {
                    parentFolderUri = createMetaDirectory(parentFolderUri, singleFolder);
                }
            }
        }
        return processLocationUri;
    }

    /**
     * Creates a new File.
     *
     * @param fileName
     *            the name of the new file
     * @return the uri of the new file
     */
    public URI createResource(String fileName) throws IOException {
        return fileManagementModule.create(null, fileName, true);
    }

    /**
     * Creates a resource at a given URI with a given name.
     *
     * @param targetFolder
     *            the URI of the target folder
     * @param name
     *            the name of the new resource
     * @return the URI of the created resource
     */
    public URI createResource(URI targetFolder, String name) throws IOException {
        return fileManagementModule.create(targetFolder, name, true);
    }

    /**
     * Writes to a file at a given URI.
     *
     * @param uri
     *            the URI, to write to.
     * @return an output stream to the file at the given URI or null
     * @throws AccessDeniedException
     *             always, because no user cannot have obtained any sufficient
     *             authorization
     * @deprecated Use {@link #writeAsCurrentUser(URI)} instead.
     */
    public OutputStream write(URI uri) throws IOException {
        return fileManagementModule.write(uri);
    }

    /**
     * Writes to a file at a given URI.
     *
     * @param uri
     *            the uri to write to
     * @param access
     *            the result of a successful lock operation that authorizes the
     *            opening of the stream
     * @return an output stream to the file at the given URI or null
     * @throws AccessDeniedException
     *             if the user does not have sufficient authorization
     * @throws IOException
     *             if write fails
     */
    public OutputStream write(URI uri, LockResult access) throws IOException {
        return fileManagementModule.write(uri, access);
    }

    /**
     * Gets and returns the name of the user whose context the code is currently
     * running in, to request or assume meta-data locks for that user. The name
     * of the user is returned, or “System”, if the code is running in the
     * system context (i.e. not running under a registered user).
     * 
     * @return the user name for locks
     */
    public static String getCurrentLockingUser() {
        UserService userService = ServiceManager.getUserService();
        User currentUser = userService.getAuthenticatedUser();
        return Objects.nonNull(currentUser) ? userService.getFullName(currentUser) : SYSTEM_LOCKING_USER;
    }

    /**
     * Reads a file at a given URI.
     *
     * @param uri
     *            the uri to read
     * @return an InputStream to read from or null
     * @throws AccessDeniedException
     *             always, because no user cannot have obtained any sufficient
     *             authorization
     * @deprecated Use {@link #readAsCurrentUser(URI)} instead.
     */
    public InputStream read(URI uri) throws IOException {
        return fileManagementModule.read(uri);
    }

    /**
     * Reads a file at a given URI.
     *
     * @param uri
     *            the URI to read from
     * @param access
     *            the result of a successful lock operation that authorizes the
     *            opening of the stream
     * @return an InputStream to read from or null
     * @throws AccessDeniedException
     *             if the user does not have sufficient authorization
     * @throws IOException
     *             if read fails
     */
    public InputStream read(URI uri, LockResult access) throws IOException {
        return fileManagementModule.read(uri, access);
    }

    /**
     * Read metadata file (meta.xml).
     * 
     * @param process
     *            for which file should be read
     * @return InputStream with metadata file
     */
    public InputStream readMetadataFile(Process process) throws IOException {
        return read(getMetadataFilePath(process));
    }

    /**
     * This function implements file renaming. Renaming of files is full of
     * mischief under Windows which unaccountably holds locks on files.
     * Sometimes running the JVM’s garbage collector puts things right.
     *
     * @param fileUri
     *            File to rename
     * @param newFileName
     *            New file name / destination
     * @throws IOException
     *             is thrown if the rename fails permanently
     */
    public URI renameFile(URI fileUri, String newFileName) throws IOException {
        return fileManagementModule.rename(fileUri, newFileName);
    }

    /**
     * Calculate all files with given file extension at specified directory
     * recursively.
     *
     * @param directory
     *            the directory to run through
     * @return number of files as Integer
     */
    public Integer getNumberOfFiles(URI directory) {
        return fileManagementModule.getNumberOfFiles(null, directory);
    }

    /**
     * Calculate all files with given file extension at specified directory
     * recursively.
     *
     * @param directory
     *            the directory to run through
     * @return number of files as Integer
     */
    public Integer getNumberOfImageFiles(URI directory) {
        return fileManagementModule.getNumberOfFiles(ImageHelper.imageNameFilter, directory);
    }

    /**
     * Get size of directory.
     *
     * @param directory
     *            URI to get size
     * @return size of directory as Long
     */
    public Long getSizeOfDirectory(URI directory) throws IOException {
        return fileManagementModule.getSizeOfDirectory(directory);
    }

    /**
     * Copy directory.
     *
     * @param sourceDirectory
     *            source file as uri
     * @param targetDirectory
     *            destination file as uri
     */
    public void copyDirectory(URI sourceDirectory, URI targetDirectory) throws IOException {
        fileManagementModule.copy(sourceDirectory, targetDirectory);
    }

    /**
     * Copies a file from a given URI to a given URI.
     *
     * @param sourceUri
     *            the uri to copy from
     * @param destinationUri
     *            the uri to copy to
     * @throws IOException
     *             if copying fails
     */
    public void copyFile(URI sourceUri, URI destinationUri) throws IOException {
        fileManagementModule.copy(sourceUri, destinationUri);
    }

    /**
     * Copies a file to a directory.
     *
     * @param sourceDirectory
     *            The source directory
     * @param targetDirectory
     *            the target directory
     * @throws IOException
     *             if copying fails.
     */
    public void copyFileToDirectory(URI sourceDirectory, URI targetDirectory) throws IOException {
        fileManagementModule.copy(sourceDirectory, targetDirectory);
    }

    /**
     * Deletes a resource at a given URI.
     *
     * @param uri
     *            the uri to delete
     * @return true, if successful, false otherwise
     * @throws IOException
     *             if get of module fails
     */
    public boolean delete(URI uri) throws IOException {
        return fileManagementModule.delete(uri);
    }

    /**
     * Checks, if a file exists.
     *
     * @param uri
     *            the URI, to check, if there is a file
     * @return true, if the file exists
     */
    public boolean fileExist(URI uri) {
        return fileManagementModule.fileExist(uri);
    }

    /**
     * Checks if a resource at a given URI is a file.
     *
     * @param uri
     *            the URI to check, if there is a file
     * @return true, if it is a file, false otherwise
     */
    public boolean isFile(URI uri) {
        return fileManagementModule.isFile(uri);
    }

    /**
     * checks, if a URI leads to a directory.
     *
     * @param dir
     *            the uri to check.
     * @return true, if it is a directory.
     */
    public boolean isDirectory(URI dir) {
        return fileManagementModule.isDirectory(dir);
    }

    /**
     * Checks if an uri is readable.
     *
     * @param uri
     *            the uri to check.
     * @return true, if it's readable, false otherwise.
     */
    public boolean canRead(URI uri) {
        return fileManagementModule.canRead(uri);
    }

    /**
     * Returns the name of a file at a given URI.
     *
     * @param uri
     *            the URI, to get the filename from.
     * @return the name of the file
     */
    public String getFileName(URI uri) {
        String fileNameWithExtension = fileManagementModule.getFileNameWithExtension(uri);
        if (fileNameWithExtension.contains(".")) {
            return fileNameWithExtension.substring(0, fileNameWithExtension.indexOf('.'));
        }
        return fileNameWithExtension;
    }

    /**
     * Returns the name of a file at a given uri.
     *
     * @param uri
     *            the URI, to get the filename from
     * @return the name of the file
     */
    public String getFileNameWithExtension(URI uri) {
        return fileManagementModule.getFileNameWithExtension(uri);
    }

    /**
     * Moves a directory from a given URI to a given URI.
     *
     * @param sourceUri
     *            the source URI
     * @param targetUri
     *            the target URI
     * @throws IOException
     *             if get of module fails
     */
    public void moveDirectory(URI sourceUri, URI targetUri) throws IOException {
        fileManagementModule.move(sourceUri, targetUri);
    }

    /**
     * Moves a file from a given URI to a given URI.
     *
     * @param sourceUri
     *            the source URI
     * @param targetUri
     *            the target URI
     * @throws IOException
     *             if get of module fails
     */
    public void moveFile(URI sourceUri, URI targetUri) throws IOException {
        fileManagementModule.move(sourceUri, targetUri);
    }

    /**
     * Get all sub URIs of an URI.
     *
     * @param uri
     *            the URI, to get the sub URIs from
     * @return a List of sub URIs
     */
    public List<URI> getSubUris(URI uri) {
        return fileManagementModule.getSubUris(null, uri);
    }

    /**
     * Get all sub URIs of an URI with a given filter.
     *
     * @param filter
     *            the filter to filter the sub URIs
     * @param uri
     *            the URI, to get the sub URIs from
     * @return a List of sub URIs
     */
    public List<URI> getSubUris(FilenameFilter filter, URI uri) {
        return fileManagementModule.getSubUris(filter, uri);
    }

    /**
     * Lists all Files at the given Path.
     *
     * @param file
     *            the directory to get the Files from
     * @return an Array of Files.
     */
    private File[] listFiles(File file) {
        File[] unchecked = file.listFiles();
        return Objects.nonNull(unchecked) ? unchecked : new File[0];
    }

    /**
     * Writes a metadata file.
     *
     * @param gdzfile
     *            the file format
     * @param process
     *            the process
     * @throws IOException
     *             if error occurs
     */
    public void writeMetadataFile(LegacyMetsModsDigitalDocumentHelper gdzfile, Process process) throws IOException {
        RulesetService rulesetService = ServiceManager.getRulesetService();
        LegacyMetsModsDigitalDocumentHelper ff;

        Ruleset ruleset = process.getRuleset();
        switch (MetadataFormat.findFileFormatsHelperByName(process.getProject().getFileFormatInternal())) {
            case METS:
                ff = new LegacyMetsModsDigitalDocumentHelper(rulesetService.getPreferences(ruleset).getRuleset());
                break;
            default:
                throw new UnsupportedOperationException("Dead code pending removal");
        }
        // createBackupFile();
        URI metadataFileUri = getMetadataFilePath(process);
        String temporaryMetadataFileName = getTemporaryMetadataFileName(metadataFileUri);

        ff.setDigitalDocument(gdzfile.getDigitalDocument());
        // ff.write(getMetadataFilePath());
        ff.write(temporaryMetadataFileName);
        File temporaryMetadataFile = new File(temporaryMetadataFileName);
        boolean backupCondition = temporaryMetadataFile.exists() && (temporaryMetadataFile.length() > 0);
        if (backupCondition) {
            createBackupFile(process);
            renameFile(Paths.get(temporaryMetadataFileName).toUri(), metadataFileUri.getRawPath());
            removePrefixFromRelatedMetsAnchorFilesFor(Paths.get(temporaryMetadataFileName).toUri());
        }
    }

    private void removePrefixFromRelatedMetsAnchorFilesFor(URI temporaryMetadataFilename) throws IOException {
        File temporaryFile = new File(temporaryMetadataFilename);
        File directoryPath = new File(temporaryFile.getParentFile().getPath());
        for (File temporaryAnchorFile : listFiles(directoryPath)) {
            String temporaryAnchorFileName = temporaryAnchorFile.toString();
            if (temporaryAnchorFile.isFile()
                    && FilenameUtils.getBaseName(temporaryAnchorFileName).startsWith(TEMPORARY_FILENAME_PREFIX)) {
                String anchorFileName = FilenameUtils.concat(FilenameUtils.getFullPath(temporaryAnchorFileName),
                    temporaryAnchorFileName.replace(TEMPORARY_FILENAME_PREFIX, ""));
                temporaryAnchorFileName = FilenameUtils.concat(FilenameUtils.getFullPath(temporaryAnchorFileName),
                    temporaryAnchorFileName);
                renameFile(Paths.get(temporaryAnchorFileName).toUri(), new File(anchorFileName).toURI().getRawPath());
            }
        }
    }

    // backup of meta.xml
    void createBackupFile(Process process) throws IOException {
        int numberOfBackups;

        numberOfBackups = ConfigCore.getIntParameter(ParameterCore.NUMBER_OF_META_BACKUPS);

        if (numberOfBackups != ConfigCore.INT_PARAMETER_NOT_DEFINED_OR_ERRONEOUS) {
            BackupFileRotation bfr = new BackupFileRotation();
            bfr.setNumberOfBackups(numberOfBackups);
            bfr.setFormat("meta.*\\.xml");
            bfr.setProcess(process);
            bfr.performBackup();
        } else {
            logger.warn("No backup configured for meta data files.");
        }
    }

    /**
     * Gets the URI of the metadata.xml of a given process.
     *
     * @param process
     *            the process to get the metadata.xml for.
     * @return The URI to the metadata.xml
     */
    public URI getMetadataFilePath(Process process) throws IOException {
        URI metadataFilePath = getProcessSubTypeURI(process, ProcessSubType.META_XML, null);
        if (!fileExist(metadataFilePath)) {
            throw new IOException(Helper.getTranslation("metadataFileNotFound", Collections.singletonList(metadataFilePath.getPath())));
        }
        return metadataFilePath;
    }

    private String getTemporaryMetadataFileName(URI fileName) {
        File temporaryFile = getFile(fileName);
        String directoryPath = temporaryFile.getParentFile().getPath();
        String temporaryFileName = TEMPORARY_FILENAME_PREFIX + temporaryFile.getName();

        return directoryPath + File.separator + temporaryFileName;
    }

    /**
     * Gets the specific IMAGE sub type.
     *
     * @param process
     *            the process to get the imageDirectory for.
     * @return The uri of the Image Directory.
     */
    public URI getImagesDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.IMAGE, null);
    }

    /**
     * Gets the URI to the ocr directory.
     *
     * @param process
     *            the process tog et the ocr directory for.
     * @return the uri to the ocr directory.
     */
    public URI getOcrDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.OCR, null);
    }

    /**
     * Gets the URI to the import directory.
     *
     * @param process
     *            the process to get the import directory for.
     * @return the uri of the import directory
     */
    public URI getImportDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.IMPORT, null);
    }

    /**
     * Gets the URI to the text directory.
     *
     * @param process
     *            the process to get the text directory for.
     * @return the uri of the text directory
     */
    public URI getTxtDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.OCR_TXT, null);
    }

    /**
     * Gets the URI to the pdf directory.
     *
     * @param process
     *            the process to get the pdf directory for.
     * @return the uri of the pdf directory
     */
    public URI getPdfDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.OCR_PDF, null);
    }

    /**
     * Gets the URI to the alto directory.
     *
     * @param process
     *            the process to get the alto directory for.
     * @return the uri of the alto directory
     */
    public URI getAltoDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.OCR_ALTO, null);
    }

    /**
     * Gets the URI to the word directory.
     *
     * @param process
     *            the process to get the word directory for.
     * @return the uri of the word directory
     */
    public URI getWordDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.OCR_WORD, null);
    }

    /**
     * Gets the URI to the template file.
     *
     * @param process
     *            the process to get the template file for.
     * @return the uri of the template file
     */
    public URI getTemplateFile(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.TEMPLATE, null);
    }

    /**
     * This method is needed for migration purposes. It maps existing filePaths
     * to the correct URI. File.separator doesn't work because on Windows it
     * appends backslash to URI.
     *
     * @param process
     *            the process, the uri is needed for.
     * @return the URI.
     */
    public URI getProcessBaseUriForExistingProcess(Process process) {
        URI processBaseUri = process.getProcessBaseUri();
        if (Objects.isNull(processBaseUri) && Objects.nonNull(process.getId())) {
            process.setProcessBaseUri(fileManagementModule.createUriForExistingProcess(process.getId().toString()));
        }
        return process.getProcessBaseUri();
    }

    /**
     * Get the URI for a process sub-location. Possible locations are listed in
     * ProcessSubType.
     *
     * @param processId
     *            the id of process to get the sublocation for
     * @param processTitle
     *            the title of process to get the sublocation for
     * @param processDataDirectory
     *            the base URI of process to get the sublocation for
     * @param processSubType
     *            The subType.
     * @param resourceName
     *            the name of the single object (e.g. image) if null, the root
     *            folder of the sublocation is returned
     * @return The URI of the requested location
     */
    public URI getProcessSubTypeURI(Integer processId, String processTitle, URI processDataDirectory,
            ProcessSubType processSubType, String resourceName) {

        if (Objects.isNull(processDataDirectory)) {
            try {
                Process process = ServiceManager.getProcessService().getById(processId);
                processDataDirectory = ServiceManager.getProcessService().getProcessDataDirectory(process);
            } catch (DAOException e) {
                processDataDirectory = URI.create(String.valueOf(processId));
            }
        }

        if (Objects.isNull(resourceName)) {
            resourceName = "";
        }
        return fileManagementModule.getProcessSubTypeUri(processDataDirectory, processTitle, processSubType,
            resourceName);
    }

    /**
     * Get's the URI for a Process Sub-location. Possible Locations are listed
     * in ProcessSubType
     *
     * @param process
     *            the process to get the sublocation for.
     * @param processSubType
     *            The subType.
     * @param resourceName
     *            the name of the single object (e.g. image) if null, the root
     *            folder of the sublocation is returned
     * @return The URI of the requested location
     */
    public URI getProcessSubTypeURI(Process process, ProcessSubType processSubType, String resourceName) {

        URI processDataDirectory = ServiceManager.getProcessService().getProcessDataDirectory(process);

        if (Objects.isNull(resourceName)) {
            resourceName = "";
        }
        return fileManagementModule.getProcessSubTypeUri(processDataDirectory,
                Helper.getNormalizedTitle(process.getTitle()), processSubType, resourceName);
    }

    /**
     * Get part of the URI for specific process.
     *
     * @param filter
     *            FilenameFilter object
     * @param processId
     *            the id of process
     * @param processTitle
     *            the title of process
     * @param processDataDirectory
     *            the base URI of process
     * @param processSubType
     *            object
     * @param resourceName
     *            as String
     * @return unmapped URI
     */
    public List<URI> getSubUrisForProcess(FilenameFilter filter, Integer processId, String processTitle,
            URI processDataDirectory, ProcessSubType processSubType, String resourceName) {
        URI processSubTypeURI = getProcessSubTypeURI(processId, processTitle, processDataDirectory, processSubType,
            resourceName);
        return getSubUris(filter, processSubTypeURI);
    }

    /**
     * Get part of the URI for specific process.
     *
     * @param filter
     *            FilenameFilter object
     * @param process
     *            object
     * @param processSubType
     *            object
     * @param resourceName
     *            as String
     * @return unmapped URI
     */
    public List<URI> getSubUrisForProcess(FilenameFilter filter, Process process, ProcessSubType processSubType,
            String resourceName) {
        URI processSubTypeURI = getProcessSubTypeURI(process, processSubType, resourceName);
        return getSubUris(filter, processSubTypeURI);
    }

    /**
     * Deletes all process directories and their content.
     *
     * @param process
     *            the process to delete the directories for.
     * @return true, if deletion was successful.
     */
    public boolean deleteProcessContent(Process process) throws IOException {
        for (ProcessSubType processSubType : ProcessSubType.values()) {
            URI processSubTypeURI = getProcessSubTypeURI(process, processSubType, null);
            if (!fileManagementModule.delete(processSubTypeURI)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the image source directory.
     *
     * @param process
     *            the process, to get the source directory for
     * @return the source directory as an URI
     */
    public URI getSourceDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.IMAGE_SOURCE, null);
    }

    /**
     * Gets the URI to the temporal directory.
     *
     * @return the URI to the temporal directory.
     */
    public URI getTemporaryDirectory() {
        return ConfigCore.getUriParameter(ParameterCore.DIR_TEMP);
    }

    /**
     * Gets the URI to the users directory.
     *
     * @return the URI to the users directory.
     */
    public URI getUsersDirectory() {
        return ConfigCore.getUriParameter(ParameterCore.DIR_USERS);
    }

    public void writeMetadataAsTemplateFile(LegacyMetsModsDigitalDocumentHelper inFile, Process process)
            throws IOException {
        inFile.write(getTemplateFile(process).toString());
    }

    /**
     * Creates a symbolic link.
     *
     * @param targetUri
     *            the target URI for the link
     * @param homeUri
     *            the home URI
     * @return true, if link creation was successful
     */
    public boolean createSymLink(URI homeUri, URI targetUri, boolean onlyRead, User user) {
        return fileManagementModule.createSymLink(homeUri, targetUri, onlyRead, user.getLogin());
    }

    /**
     * Delete a symbolic link.
     *
     * @param homeUri
     *            the URI of the home folder, where the link should be deleted
     * @return true, if deletion was successful
     */
    public boolean deleteSymLink(URI homeUri) {
        return fileManagementModule.deleteSymLink(homeUri);
    }

    public File getFile(URI uri) {
        return fileManagementModule.getFile(uri);
    }

    /**
     * Deletes the slash as first character from an uri object.
     *
     * @param uri
     *            The uri object.
     * @return The new uri object without first slash.
     */
    public URI deleteFirstSlashFromPath(URI uri) {
        String uriString = uri.getPath();
        if (uriString.startsWith("/")) {
            uriString = uriString.replaceFirst("/", "");
        }
        return URI.create(uriString);
    }

    /**
     * Creates images files by copy of a configured source dummy image at images
     * source folder of given process.
     * 
     * @param process
     *            The process object.
     * @param numberOfNewImages
     *            The number of images to be created.
     */
    public void createDummyImagesForProcess(Process process, int numberOfNewImages)
            throws IOException, URISyntaxException {
        URI imagesDirectory = getSourceDirectory(process);
        int startValue = getNumberOfFiles(imagesDirectory) + 1;
        URI dummyImage = getDummyImagePath();

        // Load number of digits to create valid filenames
        String numberOfDigits = extractNumber(ConfigCore.getParameter(ParameterCore.IMAGE_PREFIX));

        for (int i = startValue; i < startValue + numberOfNewImages; i++) {
            copyFile(dummyImage, imagesDirectory.resolve(String.format("%0" + numberOfDigits + "d", i) + ".tif"));
        }
    }

    private String extractNumber(String string) {
        return string.replaceAll("\\D+", "");
    }

    private URI getDummyImagePath() throws URISyntaxException, IOException {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        URL dummyImage = classloader.getResource("images/dummyImage.tif");
        if (Objects.nonNull(dummyImage)) {
            return dummyImage.toURI();
        } else {
            throw new IOException("No dummy image found in resources!");
        }
    }

    /**
     * Attempts to get a lock on a file.
     * 
     * @param uri
     *            URIs of the file to be locked
     * @param lockingMode
     *            type of lock to request
     * 
     * @return An object that manages allocated locks or provides information
     *         about conflict originators in case of error.
     * @throws IOException
     *             if the file does not exist or if an error occurs in disk
     *             access, e.g. because the write permission for the directory
     *             is missing
     */
    public LockResult tryLock(URI uri, LockingMode lockingMode) throws IOException {
        return tryLock(Collections.singletonList(uri), lockingMode);
    }

    /**
     * Attempts to get locks on one or more files.
     * 
     * @param uris
     *            URIs of the files to be locked
     * @param lockingMode
     *            type of lock to request (for all URIs the same)
     * 
     * @return An object that manages allocated locks or provides information
     *         about conflict originators in case of error.
     * @throws IOException
     *             if the file does not exist or if an error occurs in disk
     *             access, e.g. because the write permission for the directory
     *             is missing
     */
    public LockResult tryLock(Collection<URI> uris, LockingMode lockingMode) throws IOException {
        return tryLock(uris.parallelStream().collect(Collectors.toMap(Function.identity(), all -> lockingMode)));
    }

    /**
     * Attempts to get locks on one or more files.
     * 
     * @param requests
     *            the locks to request
     * @return An object that manages allocated locks or provides information
     *         about conflict originators in case of error.
     * @throws IOException
     *             if the file does not exist or if an error occurs in disk
     *             access, e.g. because the write permission for the directory
     *             is missing
     */
    public LockResult tryLock(Map<URI, LockingMode> requests) throws IOException {
        return fileManagementModule.tryLock(getCurrentLockingUser(), requests);
    }
}
