package com.company.leave.realtime;

import com.company.leave.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Realtime", description = "실시간 갱신")
@RestController
@RequestMapping("/api/realtime")
public class RealtimeController {

    private final RealtimeHub hub;

    public RealtimeController(RealtimeHub hub) {
        this.hub = hub;
    }

    @Operation(summary = "실시간 갱신 연결(SSE)",
            description = "데이터가 바뀌면 change 이벤트({\"origin\":\"탭 ID\"})를 보낸다. 내용은 없고, 화면이 다시 불러온다. "
                    + "30분마다 끊고 브라우저가 다시 연결한다.")
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect() {
        return hub.connect(SecurityUtils.currentEmployeeId());
    }
}
