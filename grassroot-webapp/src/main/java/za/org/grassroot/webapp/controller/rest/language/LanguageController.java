package za.org.grassroot.webapp.controller.rest.language;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import java.util.Map;

@Slf4j
@RestController @Grassroot2RestController
@Api("/v2/api/language")
@RequestMapping(value = "/v2/api/language")
public class LanguageController {


    @RequestMapping(value = "/list")
    @ApiOperation(value = "Returns a list of available languages", notes = "Returns a list of available languages")
    public ResponseEntity<Map<String, String>> listLanguages() {
        Map<String, String> languages = BaseController.getImplementedLanguages();
        return ResponseEntity.ok(languages);
    }

}
