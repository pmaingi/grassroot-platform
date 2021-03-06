package za.org.grassroot.integration.payments.peachp;

import com.fasterxml.jackson.annotation.JsonInclude;
import za.org.grassroot.integration.payments.PaymentResponse;
import za.org.grassroot.integration.payments.PaymentResultType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2016/10/31.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponsePP extends PaymentResponse {

    private String id;
    private Double amount;
    private String registrationId;
    private String paymentType;
    private String paymentBrand;

    private String recurringType;

    private PaymentResultPP result;
    private PaymentRedirectPP redirect;

    private Map<String, String> risk;
    private Map<String, String> card;
    private Map<String, String> threeDSecure;
    private Map<String, String> customParameters;

    public PaymentResponsePP() {
        // for Spring/Jackson
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public String getPaymentBrand() {
        return paymentBrand;
    }

    public void setPaymentBrand(String paymentBrand) {
        this.paymentBrand = paymentBrand;
    }

    public PaymentResultPP getResult() {
        return result;
    }

    public void setResult(PaymentResultPP result) {
        this.result = result;
    }

    public Map<String, String> getRisk() {
        return risk;
    }

    public void setRisk(Map<String, String> risk) {
        this.risk = risk;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public void setRedirect(PaymentRedirectPP redirect) { this.redirect = redirect; }

    public PaymentRedirectPP getRedirect() { return redirect; }

    public String getRecurringType() {
        return recurringType;
    }

    public void setRecurringType(String recurringType) {
        this.recurringType = recurringType;
    }

    public Map<String, String> getCard() {
        return card;
    }

    public void setCard(Map<String, String> card) {
        this.card = card;
    }

    public Map<String, String> getThreeDSecure() {
        return threeDSecure;
    }

    public void setThreeDSecure(Map<String, String> threeDSecure) {
        this.threeDSecure = threeDSecure;
    }

    public Map<String, String> getCustomParameters() {
        return customParameters;
    }

    public void setCustomParameters(Map<String, String> customParameters) {
        this.customParameters = customParameters;
    }

    @Override
    public boolean isSuccessful() { return result != null && result.isSuccessful(); }

    @Override
    public String getReference() {
        return registrationId;
    }

    @Override
    public String getThisPaymentId() {
        return id;
    }

    @Override
    public PaymentResultType getType() {
        return result == null ? PaymentResultType.FAILED_OTHER : result.getType();
    }

    @Override
    public String getDescription() {
        return result == null ? "" : result.getDescription();
    }

    @Override
    public String getRedirectUrl() {
        return redirect == null ? "" : redirect.getUrl();
    }

    @Override
    public List<Map<String, String>> getRedirectParams() {
        return redirect == null ? new ArrayList<>() : redirect.getParameters();
    }

    @Override
    public String toString() {
        return "PaymentResponsePP{" +
                "id='" + id + '\'' +
                ", registrationId='" + registrationId + '\'' +
                ", defaultPaymentType='" + paymentType + '\'' +
                ", paymentBrand='" + paymentBrand + '\'' +
                ", redirect=" + redirect +
                ", result=" + result +
                ", risk=" + risk +
                '}';
    }
}
