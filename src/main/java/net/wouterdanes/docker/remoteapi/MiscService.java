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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.base.Optional;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonStreamParser;

import net.wouterdanes.docker.remoteapi.exception.DockerException;
import net.wouterdanes.docker.remoteapi.model.ContainerCommitResponse;
import net.wouterdanes.docker.remoteapi.model.DockerVersionInfo;

import com.google.common.base.Optional;

/**
 * The class act as an interface to the "root" Remote Docker API with some "misc" service end points.
 */
public class MiscService extends BaseService {

    private static final Pattern BUILD_IMAGE_ID_EXTRACTION_PATTERN =
            Pattern.compile(".*Successfully built ([0-9a-f]+).*", Pattern.DOTALL);

    public MiscService(final String dockerApiRoot) {
        super(dockerApiRoot, "/");
    }

    /**
     * Returns the Docker version information
     *
     * @return a {@link DockerVersionInfo} instance describing this docker installation.
     */
    public DockerVersionInfo getVersionInfo() {
        String json = getServiceEndPoint()
                .path("/version")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(String.class);

        return toObject(json, DockerVersionInfo.class);
    }

    /**
     * Create a new image from a container's changes
     *
     * @param container source container
     * @param repo repository
     * @param tag tag
     * @param comment commit message
     * @param author author (e.g., "John Hannibal Smith <hannibal@a-team.com>")
     *
     * @return the ID of the created image
     */
    public String commitContainer(
            String container,
            Optional<String> repo,
            Optional<String> tag,
            Optional<String> comment,
            Optional<String> author) {

        WebTarget request = getServiceEndPoint()
                .path("/commit")
                .queryParam("container", container)
                .queryParam("repo", repo.orNull())
                .queryParam("tag", tag.orNull())
                .queryParam("comment", comment.orNull())
                .queryParam("author", author.orNull());

        String json = request
                .request(MediaType.APPLICATION_JSON_TYPE)
                .method(HttpMethod.POST, String.class);

        ContainerCommitResponse result = toObject(json, ContainerCommitResponse.class);

        return result.getId();
    }

    /**
     * Builds an image based on the passed tar archive. Optionally names &amp; tags the image
     *
     * @param tarArchive the tar archive to use as a source for the image
     * @param name       the name and optional tag of the image.
     * @return the ID of the created image
     */
    public String buildImage(byte[] tarArchive, Optional<String> name) {
        Response response = getServiceEndPoint()
                .path("/build")
                .queryParam("q", true)
                .queryParam("t", name.orNull())
                .queryParam("forcerm")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(tarArchive, "application/tar"));

        InputStream inputStream = (InputStream) response.getEntity();

        String imageId = parseSteamForImageId(inputStream);

        if (imageId == null) {
            throw new DockerException("Can't obtain ID from build output stream.");
        }

        return imageId;
    }

    private static String parseSteamForImageId(final InputStream inputStream) {
        InputStreamReader isr = new InputStreamReader(inputStream);
        BufferedReader reader = new BufferedReader(isr);

        JsonStreamParser parser = new JsonStreamParser(reader);

        String imageId = null;

        while (parser.hasNext()) {
            JsonElement element = parser.next();
            JsonObject object = element.getAsJsonObject();
            if (object.has("stream")) {
                String text = object.get("stream").getAsString();
                System.out.print(text);
                Matcher matcher = BUILD_IMAGE_ID_EXTRACTION_PATTERN.matcher(text);
                if (matcher.matches()) {
                    imageId = matcher.group(1);
                }
            }
            if (object.has("status")) {
                System.out.println(object.get("status").getAsString());
            }
            if (object.has("error")) {
                System.err.println("ERROR: " + object.get("error").getAsString());
            }
        }
        return imageId;
    }

}
