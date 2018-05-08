package za.org.grassroot.integration.socialmedia;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.integration.messaging.CreateJwtTokenRequest;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.messaging.JwtType;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service @Slf4j
public class SocialMediaBrokerImpl implements SocialMediaBroker {

    @Value("${grassroot.fb.lambda.url:http://localhost:3000}")
    private String facebookLambdaUrl;

    @Value("${grassroot.twitter.lambda.url:http://localhost:3000}")
    private String twitterLambdaUrl;

    @Value("${grassroot.images.view.url:http://localhost:8080/image}")
    private String imageBaseUri;

    // note: to replace with single WebClient when up to Spring 5
    private final RestTemplate restTemplate;
    private final JwtService jwtService;
    private final MediaFileBroker mediaFileBroker;

    public SocialMediaBrokerImpl(RestTemplate restTemplate, JwtService jwtService, MediaFileBroker mediaFileBroker) {
        this.restTemplate = restTemplate;
        this.jwtService = jwtService;
        this.mediaFileBroker = mediaFileBroker;
    }

    private UriComponentsBuilder fbBaseUri(String path) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(facebookLambdaUrl);
        builder = builder.pathSegment(path);
        return builder;
    }

    private UriComponentsBuilder twBaseUri(String path) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(twitterLambdaUrl);
        builder = builder.pathSegment(path);
        return builder;
    }

    private HttpHeaders jwtHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + jwtService.createJwt(new CreateJwtTokenRequest(JwtType.GRASSROOT_MICROSERVICE)));
        return headers;
    }

    @Override
    public List<FacebookAccount> getFacebookPages(String userUid) {
        URI getPagesUri = fbBaseUri("/facebook/pages/{userUid}").buildAndExpand(userUid).toUri();
        log.debug("constructed get pages URI: {}", getPagesUri);
        try {
            ResponseEntity<FacebookAccount[]> userPages = restTemplate.getForEntity(getPagesUri, FacebookAccount[].class);
            log.info("got these pages back: {}", Arrays.toString(userPages.getBody()));
            return userPages.getBody() != null && userPages.getBody().length > 0 ? Arrays.asList(userPages.getBody()) : new ArrayList<>();
        } catch (ResourceAccessException e) {
            log.error("can't access FB lambda");
            return new ArrayList<>();
        }
    }

    @Override
    public String initiateFacebookConnection(String userUid) {
        final URI uri = fbBaseUri("facebook/connect/request/{userUid}")
                .buildAndExpand(userUid).toUri();
        log.info("okay trying this out, URI = {}", uri.toString());
        return getRedirectUrl(uri);
    }

    @Override
    public String initiateTwitterConnection(String userUid) {
        final URI uri = twBaseUri("twitter/connect/request/{userUid}")
                .buildAndExpand(userUid).toUri();
        log.info("initiating twitter lambda, url: {}", uri);
        ResponseEntity<String> parameters = restTemplate.getForEntity(uri, String.class);
        log.info("okay got back params: {}", parameters.getBody());
        return "https://api.twitter.com/oauth/authorize?" + parameters.getBody();
    }

    @Override
    public List<FacebookAccount> completeFbConnection(String userUid, String code) {
        final URI uri = fbBaseUri("facebook/connect/done/{userUid}")
                .queryParam("code", code).buildAndExpand(userUid).toUri();

        log.info("completing FB connection, URI = {}", uri.toString());

        ResponseEntity<String> connectResponse = restTemplate.getForEntity(uri, String.class);
        return connectResponse.getStatusCode() == HttpStatus.CREATED ? getFacebookPages(userUid) : new ArrayList<>();
    }

    @Override
    public TwitterAccount completeTwitterConnection(String userUid, String oauthToken, String oauthVerifier) {
        final URI uri = twBaseUri("twitter/connect/done/{userUid}")
                .queryParam("oauth_token", oauthToken)
                .queryParam("oauth_verifier", oauthVerifier)
                .buildAndExpand(userUid).toUri();
        ResponseEntity<TwitterAccount> account = restTemplate.getForEntity(uri, TwitterAccount.class);
        return account.getBody();
    }

    @Override
    public List<GenericPostResponse> postToFacebook(List<FBPostBuilder> posts) {
        log.info("posting to facebook, here are the posts: {}", posts);
        return posts.stream().map(this::postToFb).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private GenericPostResponse postToFb(FBPostBuilder post) {
        final String pathSegment = post.hasImage() ? "facebook/photo/{userUid}" : "facebook/post/{userUid}";
        UriComponentsBuilder builder = fbBaseUri(pathSegment)
                .queryParam("message", post.getMessage());

        if (post.hasImage()) {
            final String imageUrl = imageBaseUri + "/" + post.getImageMediaType() + "/" + post.getImageKey();
            builder = builder.queryParam("imageUrl", imageUrl);
        }

        if (post.isPagePost()) {
            builder = builder.queryParam("pageId", post.getFacebookPageId());
        }

        HttpEntity entity = new HttpEntity<>(null);
        try {
            final URI postUri = builder.buildAndExpand(post.getPostingUserUid()).toUri();
            log.info("posting to URI: {}", postUri);
            ResponseEntity<GenericPostResponse> response = restTemplate.exchange(postUri, HttpMethod.POST, entity, GenericPostResponse.class);
            return handleResponse(response, "FB");
        } catch (RestClientException e) {
            log.error("Error calling FB post!", e);
            return null;
        }
    }

    @Override
    public TwitterAccount isTwitterAccountConnected(String userUid) {
        try {
            final URI uri = twBaseUri("twitter/status/{userUid}").buildAndExpand(userUid).toUri();
            ResponseEntity<TwitterAccount> twAccount = restTemplate.getForEntity(uri, TwitterAccount.class);
            log.info("got twitter account: ", twAccount);
            return twAccount.getBody();
        } catch (RestClientException e) {
            log.error("Error trying to check Twitter account: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public GenericPostResponse postToTwitter(TwitterPostBuilder post) {
        UriComponentsBuilder uriBuilder = twBaseUri("twitter/post/{userUid}")
                .queryParam("tweet", post.getMessage());

        if (!StringUtils.isEmpty(post.getImageKey())) {
            uriBuilder = uriBuilder
                    .queryParam("mediaKey", post.getImageKey())
                    .queryParam("mediaBucket", mediaFileBroker.getBucketForFunction(post.getImageMediaFunction()));
        }

        final URI uri = uriBuilder.buildAndExpand(post.getPostingUserUid()).toUri();

        HttpEntity entity = new HttpEntity<>(jwtHeaders());

        try {
            ResponseEntity<GenericPostResponse> response = restTemplate.exchange(uri, HttpMethod.POST, entity, GenericPostResponse.class);
            return handleResponse(response, "Twitter");
        } catch (RestClientException e) {
            log.error("Error calling Twitter post!", e);
            return null;
        }
    }

    @Override
    public boolean removeIntegration(String userUid, String providerId) {
        try {
            if ("facebook".equals(providerId)) {
                final URI uri = twBaseUri("facebook/remove/{userUid}").buildAndExpand(userUid).toUri();
                restTemplate.getForEntity(uri, String.class);
                return true;
            } else if ("twitter".equals(providerId)) {
                final URI uri = twBaseUri("twitter/remove/{userUid}").buildAndExpand(userUid).toUri();
                restTemplate.getForEntity(uri, String.class);
                return true;
            } else {
                return false;
            }
        } catch (RestClientException e) {
            return false;
        }
    }

    private String getRedirectUrl(URI uri) {
        try {
            ResponseEntity<RedirectView> view = restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(jwtHeaders()),
                    RedirectView.class);
            log.info("and returned view with headers = {} and url = {}", view.getHeaders(), view.getHeaders().get("Location"));
            return view.getHeaders().get("Location").get(0);
        } catch (RestClientException e) {
            log.error("error calling integration broker", e);
            return null;
        }
    }

    private <T extends ServiceResponse> T handleResponse(ResponseEntity<T> response, String provider) {
        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        } else {
            log.error("Error! Could not complete call to {} provider, response: {}", provider, response);
            return null;
        }
    }

}
