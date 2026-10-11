package demo.claimtriage;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The front door: POST a claim as plain text, get back the id of the process instance that handles it. */
@RestController
public class ClaimController {

  private final CamundaClient camunda;

  public ClaimController(CamundaClient camunda) {
    this.camunda = camunda;
  }

  @PostMapping(path = "/claims", consumes = MediaType.TEXT_PLAIN_VALUE)
  public Map<String, Object> submit(@RequestBody String claimText) {
    String claimId = "CLM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    ProcessInstanceEvent instance = camunda.newCreateInstanceCommand()
        .bpmnProcessId("claim-triage")
        .latestVersion()
        .variables(Map.of("claimId", claimId, "claimText", claimText))
        .send()
        .join();

    return Map.of("claimId", claimId, "processInstanceKey", instance.getProcessInstanceKey());
  }
}
