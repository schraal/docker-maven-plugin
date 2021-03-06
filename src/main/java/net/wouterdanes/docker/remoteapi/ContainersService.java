/*
    Copyright 2014 Wouter Danes

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

*/

package net.wouterdanes.docker.remoteapi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.wouterdanes.docker.remoteapi.exception.ContainerNotFoundException;
import net.wouterdanes.docker.remoteapi.exception.DockerException;
import net.wouterdanes.docker.remoteapi.model.ContainerCreateRequest;
import net.wouterdanes.docker.remoteapi.model.ContainerCreateResponse;
import net.wouterdanes.docker.remoteapi.model.ContainerInspectionResult;
import net.wouterdanes.docker.remoteapi.model.ContainerStartRequest;

/**
 * This class is responsible for talking to the Docker Remote API "containers" endpoint.<br> See <a
 * href="http://docs.docker.io/reference/api/docker_remote_api_v1.12/#21-containers">
 * http://docs.docker.io/reference/api/docker_remote_api_v1.12/#21-containers</a>
 */
public class ContainersService extends BaseService {

    public ContainersService(String dockerApiRoot) {
        super(dockerApiRoot, "/containers");
    }

    public String createContainer(ContainerCreateRequest request) {
        String createResponseStr;
        System.out.println(request);
		System.out.println(getServiceEndPoint());
		System.out.println(getServiceEndPoint().path("/create"));
		System.out.println(getServiceEndPoint().path("/create").request(MediaType.APPLICATION_JSON_TYPE));
		System.out.println(Entity.entity(toJson(request), MediaType.APPLICATION_JSON_TYPE));
        try {
            createResponseStr = getServiceEndPoint()
                    .path("/create")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.entity(toJson(request), MediaType.APPLICATION_JSON_TYPE), String.class);
        } catch (WebApplicationException e) {
            throw makeImageTargetingException(request.getImage(), e);
        }
        System.out.println(createResponseStr);
        ContainerCreateResponse createResponse = toObject(createResponseStr, ContainerCreateResponse.class);
        return createResponse.getId();
    }

    public void startContainer(String id, ContainerStartRequest configuration) {
          Response response = getServiceEndPoint()
                .path(id)
                .path("/start")
                .request()
                .post(Entity.entity(toJson(configuration), MediaType.APPLICATION_JSON_TYPE));

        Response.StatusType statusInfo = response.getStatusInfo();
        response.close();

        checkContainerTargetingResponse(id, statusInfo);
    }

    public void killContainer(String id) {
        Response response = getServiceEndPoint()
                .path(id)
                .path("/kill")
                .request()
                .method(HttpMethod.POST);

        Response.StatusType statusInfo = response.getStatusInfo();
        response.close();

        checkContainerTargetingResponse(id, statusInfo);
    }

    public void deleteContainer(String id) {
        Response response = getServiceEndPoint()
                .path(id)
                .request()
                .delete();

        Response.StatusType statusInfo = response.getStatusInfo();
        response.close();

        checkContainerTargetingResponse(id, statusInfo);
    }

    private static void checkContainerTargetingResponse(final String id, final Response.StatusType statusInfo) {
        switch (statusInfo.getStatusCode()) {
            case 404:
                throw new ContainerNotFoundException(id);
            case 500:
                throw new DockerException(statusInfo.getReasonPhrase());
        }
    }

    public ContainerInspectionResult inspectContainer(final String containerId) {
        String json = getServiceEndPoint()
                .path(containerId)
                .path("json")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(String.class);

        return toObject(json, ContainerInspectionResult.class);
    }

    public String getLogs(final String containerId) {
        byte[] bytes = getServiceEndPoint()
                .path(containerId)
                .path("logs")
                .queryParam("stdout", 1)
                .queryParam("stderr", 1)
                .request("application/vnd.docker.raw-stream")
                .get(byte[].class);

        // To see how docker returns the logs and why it's parsed like this:
        // http://docs.docker.com/v1.2/reference/api/docker_remote_api_v1.14/#attach-to-a-container
        StringBuilder logs = new StringBuilder();
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);

        while (bb.hasRemaining()) {
            bb.position(bb.position() + 4);
            int frameLength = bb.getInt();
            byte[] frame = new byte[frameLength];
            bb.get(frame);
            logs.append(new String(frame, Charset.forName("UTF-8")));
        }

        return logs.toString();
    }
}
