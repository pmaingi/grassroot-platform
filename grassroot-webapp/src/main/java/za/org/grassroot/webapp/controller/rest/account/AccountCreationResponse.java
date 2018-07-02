package za.org.grassroot.webapp.controller.rest.account;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountCreationResponse {

    public String accountId;
    public List<String> errorAdmins;

}
