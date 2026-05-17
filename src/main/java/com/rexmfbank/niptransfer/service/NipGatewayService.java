package com.rexmfbank.niptransfer.service;

import com.rexmfbank.niptransfer.model.Transaction;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates NIBSS NIP 2.0 gateway integration.
 *
 * In production this would:
 * - Establish mTLS connection to NIBSS gateway
 * - Sign requests with HSM-managed certificates
 * - Handle NIBSS response codes and retry logic
 * - Maintain a connection pool with circuit breaker (Resilience4j)
 *
 * For this demo, it simulates realistic response scenarios
 * including success, insufficient funds, and invalid account.
 */
@Service
@Slf4j
public class NipGatewayService {

    // Nigerian bank codes mapped to names (subset for demo)
    private static final Map<String, String> BANK_CODES = Map.ofEntries(
            Map.entry("058", "GTBank"),
            Map.entry("044", "Access Bank"),
            Map.entry("011", "First Bank"),
            Map.entry("057", "Zenith Bank"),
            Map.entry("033", "UBA"),
            Map.entry("076", "Polaris Bank"),
            Map.entry("050", "EcoBank Nigeria"),
            Map.entry("215", "Unity Bank"),
            Map.entry("090175", "Rex Microfinance Bank"),
            Map.entry("090001", "ASOSavings MFB"),
            Map.entry("000013", "GTBank (Commercial)")
    );

    /**
     * NIP Name Enquiry (Account Verification) — confirms beneficiary
     * account exists before funds are sent. CBN mandate since 2014.
     *
     * @param accountNumber 10-digit NUBAN
     * @param bankCode      CBN institution code
     * @return Verified account name
     */
    public String nameEnquiry(String accountNumber, String bankCode) {
        log.info("NIP Name Enquiry: account={}, bankCode={}", accountNumber, bankCode);

        // Simulate network call to NIBSS
        simulateNetworkLatency(100, 300);

        // In production: call NIBSS /nameenquiry endpoint
        // For demo: generate a plausible account name
        String bankName = BANK_CODES.getOrDefault(bankCode, "Unknown Bank");
        log.info("Name Enquiry successful: bank={}", bankName);

        return generateSimulatedAccountName();
    }

    /**
     * NIP Funds Transfer — submits transfer instruction to NIBSS.
     *
     * NIBSS NIP 2.0 response codes:
     * 00 - Approved/Completed
     * 09 - Request received, pending processing
     * 25 - Unable to locate record (invalid account)
     * 51 - Insufficient funds
     * 65 - Exceeds withdrawal frequency limit
     * 68 - Response received too late
     * 96 - System malfunction
     */
    public NipResponse sendFundsTransfer(Transaction transaction) {
        log.info("Dispatching NIP funds transfer: sessionId={}, amount={}, to={}@{}",
                transaction.getSessionId(),
                transaction.getAmount(),
                transaction.getBeneficiaryAccountNumber(),
                transaction.getBeneficiaryBankCode());

        simulateNetworkLatency(200, 800);

        // Simulate realistic NIP response distribution
        // Real-world success rate for valid transfers is ~97%
        return simulateNipResponse(transaction.getAmount());
    }

    private NipResponse simulateNipResponse(BigDecimal amount) {
        int roll = ThreadLocalRandom.current().nextInt(100);

        if (roll < 90) {
            // 90% success rate
            return NipResponse.builder()
                    .responseCode("00")
                    .responseMessage("Transaction Successful")
                    .build();
        } else if (roll < 95) {
            // 5% - insufficient funds
            return NipResponse.builder()
                    .responseCode("51")
                    .responseMessage("Insufficient Funds")
                    .build();
        } else if (roll < 98) {
            // 3% - pending (NIBSS still processing)
            return NipResponse.builder()
                    .responseCode("09")
                    .responseMessage("Transaction Received - Processing")
                    .build();
        } else {
            // 2% - system/timeout
            return NipResponse.builder()
                    .responseCode("96")
                    .responseMessage("System Malfunction - Please retry")
                    .build();
        }
    }

    private void simulateNetworkLatency(int minMs, int maxMs) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(minMs, maxMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String generateSimulatedAccountName() {
        String[] firstNames = {"ADEYEMI", "CHUKWUEMEKA", "FATIMA", "IBRAHIM", "NGOZI",
                               "OLUWASEUN", "BABATUNDE", "AMAKA", "SEGUN", "CHIOMA"};
        String[] lastNames = {"JOHNSON", "OKAFOR", "BELLO", "ADESANYA", "NWACHUKWU",
                              "ADEWALE", "IBRAHIM", "EZE", "OKONKWO", "ADELEKE"};

        int fi = ThreadLocalRandom.current().nextInt(firstNames.length);
        int li = ThreadLocalRandom.current().nextInt(lastNames.length);
        return lastNames[li] + " " + firstNames[fi];
    }

    @Data
    @Builder
    public static class NipResponse {
        private String responseCode;
        private String responseMessage;
    }
}
