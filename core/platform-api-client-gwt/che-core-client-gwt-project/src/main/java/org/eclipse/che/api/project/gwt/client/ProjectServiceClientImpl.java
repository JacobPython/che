/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.project.gwt.client;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;

import org.eclipse.che.api.machine.gwt.client.DevMachine;
import org.eclipse.che.api.machine.gwt.client.WsAgentStateController;
import org.eclipse.che.api.project.shared.dto.CopyOptions;
import org.eclipse.che.api.project.shared.dto.ItemReference;
import org.eclipse.che.api.project.shared.dto.MoveOptions;
import org.eclipse.che.api.project.shared.dto.SourceEstimation;
import org.eclipse.che.api.project.shared.dto.TreeElement;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.api.promises.client.callback.AsyncPromiseHelper;
import org.eclipse.che.api.promises.client.callback.PromiseHelper;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.api.workspace.shared.dto.SourceStorageDto;
import org.eclipse.che.ide.MimeType;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.resource.Path;
import org.eclipse.che.ide.rest.AsyncRequestCallback;
import org.eclipse.che.ide.rest.AsyncRequestFactory;
import org.eclipse.che.ide.rest.DtoUnmarshallerFactory;
import org.eclipse.che.ide.rest.NoOpUnmarshaller;
import org.eclipse.che.ide.rest.StringUnmarshaller;
import org.eclipse.che.ide.ui.loaders.request.LoaderFactory;
import org.eclipse.che.ide.websocket.Message;
import org.eclipse.che.ide.websocket.MessageBuilder;
import org.eclipse.che.ide.websocket.MessageBus;
import org.eclipse.che.ide.websocket.WebSocketException;
import org.eclipse.che.ide.websocket.rest.RequestCallback;

import javax.validation.constraints.NotNull;
import java.util.List;

import static com.google.gwt.http.client.RequestBuilder.DELETE;
import static com.google.gwt.http.client.RequestBuilder.POST;
import static com.google.gwt.http.client.RequestBuilder.PUT;
import static org.eclipse.che.api.promises.client.callback.PromiseHelper.newCallback;
import static org.eclipse.che.api.promises.client.callback.PromiseHelper.newPromise;
import static org.eclipse.che.ide.MimeType.APPLICATION_JSON;
import static org.eclipse.che.ide.rest.HTTPHeader.ACCEPT;
import static org.eclipse.che.ide.rest.HTTPHeader.CONTENTTYPE;
import static org.eclipse.che.ide.rest.HTTPHeader.CONTENT_TYPE;

/**
 * Implementation of {@link ProjectServiceClient}.
 *
 * @author Vitaly Parfonov
 * @author Artem Zatsarynnyi
 * @author Valeriy Svydenko
 */
public class ProjectServiceClientImpl implements ProjectServiceClient {
    private final WsAgentStateController wsAgentStateController;
    private final LoaderFactory          loaderFactory;
    private final AsyncRequestFactory    asyncRequestFactory;
    private final DtoFactory             dtoFactory;
    private final DtoUnmarshallerFactory dtoUnmarshaller;

    private static final NoOpUnmarshaller noOpUnmarshaller;

    static {
        noOpUnmarshaller = new NoOpUnmarshaller();
    }

    @Inject
    protected ProjectServiceClientImpl(WsAgentStateController wsAgentStateController,
                                       LoaderFactory loaderFactory,
                                       AsyncRequestFactory asyncRequestFactory,
                                       DtoFactory dtoFactory,
                                       DtoUnmarshallerFactory dtoUnmarshaller) {
        this.wsAgentStateController = wsAgentStateController;
        this.loaderFactory = loaderFactory;
        this.asyncRequestFactory = asyncRequestFactory;
        this.dtoFactory = dtoFactory;
        this.dtoUnmarshaller = dtoUnmarshaller;
    }

    @Override
    public void getProjects(DevMachine devMachine, AsyncRequestCallback<List<ProjectConfigDto>> callback) {
        asyncRequestFactory.createGetRequest(devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId())
                           .header(ACCEPT, MimeType.APPLICATION_JSON)
                           .loader(loaderFactory.newLoader("Getting projects..."))
                           .send(callback);
    }

    @Override
    public Promise<List<ProjectConfigDto>> getProjects(final DevMachine devMachine) {
        return newPromise(new AsyncPromiseHelper.RequestCall<List<ProjectConfigDto>>() {
            @Override
            public void makeCall(AsyncCallback<List<ProjectConfigDto>> callback) {
                getProjects(devMachine, newCallback(callback, dtoUnmarshaller.newListUnmarshaller(ProjectConfigDto.class)));
            }
        });
    }

    @Override
    public void getProject(DevMachine devMachine, String path, AsyncRequestCallback<ProjectConfigDto> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + normalizePath(path);
        asyncRequestFactory.createGetRequest(requestUrl)
                           .header(ACCEPT, MimeType.APPLICATION_JSON)
                           .loader(loaderFactory.newLoader("Getting project..."))
                           .send(callback);
    }

    @Override
    public Promise<ProjectConfigDto> getProject(DevMachine devMachine, String path) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + normalizePath(path);
        return asyncRequestFactory.createGetRequest(requestUrl)
                                  .header(ACCEPT, MimeType.APPLICATION_JSON)
                                  .loader(loaderFactory.newLoader("Getting project..."))
                                  .send(dtoUnmarshaller.newUnmarshaller(ProjectConfigDto.class));
    }

    @Override
    public void getItem(DevMachine devMachine, String path, AsyncRequestCallback<ItemReference> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/item" + normalizePath(path);
        asyncRequestFactory.createGetRequest(requestUrl)
                           .header(ACCEPT, MimeType.APPLICATION_JSON)
                           .loader(loaderFactory.newLoader("Getting item..."))
                           .send(callback);
    }

    @Override
    public void createProject(DevMachine devMachine,
                              ProjectConfigDto projectConfig,
                              AsyncRequestCallback<ProjectConfigDto> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId();
        asyncRequestFactory.createPostRequest(requestUrl, projectConfig)
                           .header(ACCEPT, MimeType.APPLICATION_JSON)
                           .loader(loaderFactory.newLoader("Creating project..."))
                           .send(callback);
    }

    @Override
    public void estimateProject(DevMachine devMachine,
                                String path,
                                String projectType,
                                AsyncRequestCallback<SourceEstimation> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/estimate" + normalizePath(path) + "?type=" + projectType;
        asyncRequestFactory.createGetRequest(requestUrl)
                           .header(ACCEPT, MimeType.APPLICATION_JSON)
                           .loader(loaderFactory.newLoader("Estimating project..."))
                           .send(callback);
    }

    @Override
    public void resolveSources(DevMachine devMachine, String path, AsyncRequestCallback<List<SourceEstimation>> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/resolve" + normalizePath(path);
        asyncRequestFactory.createGetRequest(requestUrl)
                           .header(ACCEPT, MimeType.APPLICATION_JSON)
                           .loader(loaderFactory.newLoader("Resolving sources..."))
                           .send(callback);
    }

    @Override
    public Promise<List<SourceEstimation>> resolveSources(DevMachine devMachine, Path path) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/resolve" + normalizePath(path.toString());
        return asyncRequestFactory.createGetRequest(requestUrl)
                                  .header(ACCEPT, MimeType.APPLICATION_JSON)
                                  .loader(loaderFactory.newLoader("Resolving sources..."))
                                  .send(dtoUnmarshaller.newListUnmarshaller(SourceEstimation.class));
    }


    @Override
    public void getModules(DevMachine devMachine, String path, AsyncRequestCallback<List<ProjectConfigDto>> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/modules" + normalizePath(path);
        asyncRequestFactory.createGetRequest(requestUrl)
                           .header(ACCEPT, MimeType.APPLICATION_JSON)
                           .loader(loaderFactory.newLoader("Getting modules..."))
                           .send(callback);
    }

    @Override
    public void createModule(DevMachine devMachine,
                             String parentProjectPath,
                             ProjectConfigDto projectConfig,
                             AsyncRequestCallback<ProjectConfigDto> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + normalizePath(parentProjectPath);
        asyncRequestFactory.createPostRequest(requestUrl, projectConfig)
                           .header(ACCEPT, MimeType.APPLICATION_JSON)
                           .loader(loaderFactory.newLoader("Creating module..."))
                           .send(callback);
    }

    @Override
    public void updateProject(DevMachine devMachine,
                              String path,
                              ProjectConfigDto projectConfig,
                              AsyncRequestCallback<ProjectConfigDto> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + normalizePath(path);
        asyncRequestFactory.createRequest(PUT, requestUrl, projectConfig, false)
                           .header(CONTENT_TYPE, MimeType.APPLICATION_JSON)
                           .header(ACCEPT, MimeType.APPLICATION_JSON)
                           .loader(loaderFactory.newLoader("Updating project..."))
                           .send(callback);
    }

    @Override
    public Promise<ProjectConfigDto> updateProject(DevMachine devMachine, Path path, ProjectConfigDto projectConfig) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + normalizePath(path.toString());
        return asyncRequestFactory.createRequest(PUT, requestUrl, projectConfig, false)
                                  .header(CONTENT_TYPE, MimeType.APPLICATION_JSON)
                                  .header(ACCEPT, MimeType.APPLICATION_JSON)
                                  .loader(loaderFactory.newLoader("Updating project..."))
                                  .send(dtoUnmarshaller.newUnmarshaller(ProjectConfigDto.class));
    }

    @Override
    public void createFile(DevMachine devMachine,
                           String parentPath,
                           String name,
                           String content,
                           AsyncRequestCallback<ItemReference> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/file" + normalizePath(parentPath) + "?name=" + name;
        asyncRequestFactory.createPostRequest(requestUrl, null)
                           .data(content)
                           .loader(loaderFactory.newLoader("Creating file..."))
                           .send(callback);
    }

    @Override
    public void getFileContent(DevMachine devMachine, String path, AsyncRequestCallback<String> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/file" + normalizePath(path);
        asyncRequestFactory.createGetRequest(requestUrl)
                           .loader(loaderFactory.newLoader("Loading file content..."))
                           .send(callback);
    }

    @Override
    public void updateFile(DevMachine devMachine, String path, String content, AsyncRequestCallback<Void> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/file" + normalizePath(path);
        asyncRequestFactory.createRequest(PUT, requestUrl, null, false)
                           .data(content)
                           .loader(loaderFactory.newLoader("Updating file..."))
                           .send(callback);
    }

    @Override
    public void createFolder(DevMachine devMachine, String path, AsyncRequestCallback<ItemReference> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/folder" + normalizePath(path);
        asyncRequestFactory.createPostRequest(requestUrl, null)
                           .loader(loaderFactory.newLoader("Creating folder..."))
                           .send(callback);
    }

    @Override
    public void delete(DevMachine devMachine, String path, AsyncRequestCallback<Void> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + normalizePath(path);
        asyncRequestFactory.createRequest(DELETE, requestUrl, null, false)
                           .loader(loaderFactory.newLoader("Deleting project..."))
                           .send(callback);
    }

    @Override
    public void deleteModule(DevMachine devMachine, String pathToParent, String modulePath, AsyncRequestCallback<Void> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/module" + normalizePath(pathToParent) + "?module=" + modulePath;
        asyncRequestFactory.createRequest(DELETE, requestUrl, null, false)
                           .loader(loaderFactory.newLoader("Deleting module..."))
                           .send(callback);
    }

    @Override
    public void copy(DevMachine devMachine, String path, String newParentPath, String newName, AsyncRequestCallback<Void> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/copy" + normalizePath(path) + "?to=" + newParentPath;

        final CopyOptions copyOptions = dtoFactory.createDto(CopyOptions.class);
        copyOptions.setName(newName);
        copyOptions.setOverWrite(false);

        asyncRequestFactory.createPostRequest(requestUrl, copyOptions)
                           .loader(loaderFactory.newLoader("Copying..."))
                           .send(callback);
    }

    @Override
    public void move(DevMachine devMachine, String path, String newParentPath, String newName, AsyncRequestCallback<Void> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/move" + normalizePath(path) + "?to=" + newParentPath;

        final MoveOptions moveOptions = dtoFactory.createDto(MoveOptions.class);
        moveOptions.setName(newName);
        moveOptions.setOverWrite(false);

        asyncRequestFactory.createPostRequest(requestUrl, moveOptions)
                           .loader(loaderFactory.newLoader("Moving..."))
                           .send(callback);
    }

    @Override
    public void rename(DevMachine devMachine, String path, String newName, String newMediaType, AsyncRequestCallback<Void> callback) {
        final Path source = Path.valueOf(path);
        final Path sourceParent = source.removeLastSegments(1);
        move(devMachine, source.toString(), sourceParent.toString(), newName, callback);
    }

    /**
     * Imports sources project.
     */
    @Override
    public Promise<Void> importProject(final DevMachine devMachine,
                                       final String path,
                                       final SourceStorageDto sourceStorage) {
        return PromiseHelper.newPromise(new AsyncPromiseHelper.RequestCall<Void>() {
            @Override
            public void makeCall(final AsyncCallback<Void> callback) {
                String url = "/project/" + devMachine.getId() + "/import" + normalizePath(path);
                MessageBuilder builder = new MessageBuilder(POST, url);

                builder.data(dtoFactory.toJson(sourceStorage)).header(CONTENTTYPE, APPLICATION_JSON);
                final Message message = builder.build();
                wsAgentStateController.getMessageBus().then(new Operation<MessageBus>() {
                    @Override
                    public void apply(MessageBus messageBus) throws OperationException {
                        try {
                            messageBus.send(message, new RequestCallback<Void>() {
                                @Override
                                protected void onSuccess(Void result) {
                                    callback.onSuccess(result);
                                }

                                @Override
                                protected void onFailure(Throwable exception) {
                                    callback.onFailure(exception);
                                }
                            });
                        } catch (WebSocketException e) {
                            callback.onFailure(e);
                        }
                    }
                }).catchError(new Operation<PromiseError>() {
                    @Override
                    public void apply(PromiseError arg) throws OperationException {
                        callback.onFailure(arg.getCause());
                    }
                });
            }
        });
    }

    private void sendMessageToWS(final @NotNull Message message, final @NotNull RequestCallback<?> callback) {
        wsAgentStateController.getMessageBus().then(new Operation<MessageBus>() {
            @Override
            public void apply(MessageBus arg) throws OperationException {
                try {
                    arg.send(message, callback);
                } catch (WebSocketException e) {
                    throw new OperationException(e.getMessage(), e);
                }
            }
        });
    }

    @Override
    public void getChildren(DevMachine devMachine, String path, AsyncRequestCallback<List<ItemReference>> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/children" + normalizePath(path);
        asyncRequestFactory.createGetRequest(requestUrl)
                           .header(ACCEPT, MimeType.APPLICATION_JSON)
                           .send(callback);
    }

    @Override
    public void getTree(DevMachine devMachine, String path, int depth, AsyncRequestCallback<TreeElement> callback) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/tree" + normalizePath(path) + "?depth=" + depth;
        asyncRequestFactory.createGetRequest(requestUrl)
                           .header(ACCEPT, MimeType.APPLICATION_JSON)
                           .loader(loaderFactory.newLoader("Reading project..."))
                           .send(callback);
    }

    @Override
    public Promise<List<ItemReference>> search(DevMachine devMachine, QueryExpression expression) {
        StringBuilder requestUrl = new StringBuilder(devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/search");
        if (expression.getPath() != null) {
            requestUrl.append(normalizePath(expression.getPath()));
        } else {
            requestUrl.append('/');
        }

        StringBuilder queryParameters = new StringBuilder();
        if (expression.getName() != null && !expression.getName().isEmpty()) {
            queryParameters.append("&name=").append(expression.getName());
        }
        if (expression.getText() != null && !expression.getText().isEmpty()) {
            queryParameters.append("&text=").append(expression.getText());
        }
        if (expression.getMaxItems() != 0) {
            queryParameters.append("&maxItems=").append(expression.getMaxItems());
        }
        if (expression.getSkipCount() != 0) {
            queryParameters.append("&skipCount=").append(expression.getSkipCount());
        }

        return asyncRequestFactory.createGetRequest(requestUrl.toString() + queryParameters.toString().replaceFirst("&", "?"))
                                  .header(ACCEPT, MimeType.APPLICATION_JSON)
                                  .loader(loaderFactory.newLoader("Searching..."))
                                  .send(dtoUnmarshaller.newListUnmarshaller(ItemReference.class));
    }

    /**
     * Normalizes the path by adding a leading '/' if it doesn't exist.
     * Also escapes some special characters.
     * <p/>
     * See following javascript functions for details:
     * escape() will not encode: @ * / +
     * encodeURI() will not encode: ~ ! @ # $ & * ( ) = : / , ; ? + '
     * encodeURIComponent() will not encode: ~ ! * ( ) '
     *
     * @param path
     *         path to normalize
     * @return normalized path
     */
    private String normalizePath(String path) {
        while (path.indexOf('+') >= 0) {
            path = path.replace("+", "%2B");
        }

        return path.startsWith("/") ? path : '/' + path;
    }

    @Override
    public Promise<ProjectConfigDto> createProject(DevMachine devMachine, ProjectConfigDto config) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId();

        return asyncRequestFactory.createPostRequest(requestUrl, config)
                                  .header(ACCEPT, MimeType.APPLICATION_JSON)
                                  .loader(loaderFactory.newLoader("Creating project..."))
                                  .send(dtoUnmarshaller.newUnmarshaller(ProjectConfigDto.class));
    }

    @Override
    public Promise<ItemReference> createFile(DevMachine devMachine, Path path, String content) {

        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/file" + normalizePath(path.parent().toString()) + "?name=" + path.lastSegment();

        return asyncRequestFactory.createPostRequest(requestUrl, null)
                                  .data(content)
                                  .loader(loaderFactory.newLoader("Creating file..."))
                                  .send(dtoUnmarshaller.newUnmarshaller(ItemReference.class));
    }

    @Override
    public Promise<String> readFile(DevMachine devMachine, Path path) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/file" + normalizePath(path.toString());

        return asyncRequestFactory.createGetRequest(requestUrl)
                                  .loader(loaderFactory.newLoader("Loading file content..."))
                                  .send(new StringUnmarshaller());
    }

    @Override
    public Promise<Void> writeFile(DevMachine devMachine, Path path, String content) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/file" + normalizePath(path.toString());

        return asyncRequestFactory.createRequest(PUT, requestUrl, null, false)
                                  .data(content)
                                  .loader(loaderFactory.newLoader("Updating file..."))
                                  .send(noOpUnmarshaller);
    }

    @Override
    public Promise<ItemReference> createFolder(DevMachine devMachine, Path path) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/folder" + normalizePath(path.toString());

        return asyncRequestFactory.createPostRequest(requestUrl, null)
                                  .loader(loaderFactory.newLoader("Creating folder..."))
                                  .send(dtoUnmarshaller.newUnmarshaller(ItemReference.class));
    }

    @Override
    public Promise<Void> delete(DevMachine devMachine, Path path) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + normalizePath(path.toString());

        return asyncRequestFactory.createRequest(DELETE, requestUrl, null, false)
                                  .loader(loaderFactory.newLoader("Deleting project..."))
                                  .send(noOpUnmarshaller);
    }

    @Override
    public Promise<Void> copy(DevMachine devMachine, Path source, Path target, String newName, boolean overwrite) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/copy" + normalizePath(source.toString()) + "?to=" + target.toString();

        final CopyOptions copyOptions = dtoFactory.createDto(CopyOptions.class);
        copyOptions.setName(newName);
        copyOptions.setOverWrite(overwrite);

        return asyncRequestFactory.createPostRequest(requestUrl, copyOptions)
                                  .loader(loaderFactory.newLoader("Copying..."))
                                  .send(noOpUnmarshaller);
    }

    @Override
    public Promise<Void> move(DevMachine devMachine, Path source, Path target, String newName, boolean overwrite) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/move" + normalizePath(source.toString()) + "?to=" + target.toString();

        final MoveOptions moveOptions = dtoFactory.createDto(MoveOptions.class);
        moveOptions.setName(newName);
        moveOptions.setOverWrite(overwrite);

        return asyncRequestFactory.createPostRequest(requestUrl, moveOptions)
                                  .loader(loaderFactory.newLoader("Moving..."))
                                  .send(noOpUnmarshaller);
    }

    @Override
    public Promise<TreeElement> getTree(DevMachine devMachine, Path path, int depth, boolean includeFiles) {
        final String requestUrl =
                devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/tree" + normalizePath(path.toString()) + "?depth=" + depth + "&includeFiles=" +
                includeFiles;

        return asyncRequestFactory.createGetRequest(requestUrl)
                                  .header(ACCEPT, MimeType.APPLICATION_JSON)
                                  .loader(loaderFactory.newLoader("Reading structure..."))
                                  .send(dtoUnmarshaller.newUnmarshaller(TreeElement.class));
    }

    @Override
    public Promise<ItemReference> getItem(DevMachine devMachine, Path path) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + "/item" + normalizePath(path.toString());

        return asyncRequestFactory.createGetRequest(requestUrl)
                                  .header(ACCEPT, MimeType.APPLICATION_JSON)
                                  .loader(loaderFactory.newLoader("Getting item..."))
                                  .send(dtoUnmarshaller.newUnmarshaller(ItemReference.class));
    }

    @Override
    public Promise<ProjectConfigDto> getProject(DevMachine devMachine, Path path) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + normalizePath(path.toString());
        return asyncRequestFactory.createGetRequest(requestUrl)
                                  .header(ACCEPT, MimeType.APPLICATION_JSON)
                                  .loader(loaderFactory.newLoader("Getting project..."))
                                  .send(dtoUnmarshaller.newUnmarshaller(ProjectConfigDto.class));
    }

    @Override
    public Promise<ProjectConfigDto> updateProject(DevMachine devMachine, ProjectConfigDto descriptor) {
        final String requestUrl = devMachine.getWsAgentBaseUrl() + "/project/" + devMachine.getId() + normalizePath(descriptor.getPath());
        return asyncRequestFactory.createRequest(PUT, requestUrl, descriptor, false)
                                  .header(CONTENT_TYPE, MimeType.APPLICATION_JSON)
                                  .header(ACCEPT, MimeType.APPLICATION_JSON)
                                  .loader(loaderFactory.newLoader("Updating project..."))
                                  .send(dtoUnmarshaller.newUnmarshaller(ProjectConfigDto.class));
    }
}