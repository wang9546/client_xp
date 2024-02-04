package org.prompt.client;

import lombok.Data;

import java.util.List;

@Data
public class EventResp {
    private String id;
    private Long created;
    private String model;
    private List<ChoicesData> choices;

    @Data
    public static class ChoicesData {
        private Integer index;
        private List<DeltaData> delta;
        private String finish_reason;
        private UsageData usage;
    }
    @Data
    public static class DeltaData {
        private String role;
        private String content;
        public DeltaData(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
    @Data
    public static class UsageData {
        private Integer prompt_tokens;
        private Integer completion_tokens;
        private Integer total_tokens;

        public UsageData(Integer prompt_tokens, Integer completion_tokens, Integer total_tokens) {
            this.prompt_tokens = prompt_tokens;
            this.completion_tokens = completion_tokens;
            this.total_tokens = total_tokens;
        }
    }

}
