package com.trae.watch.data.net

/** 从反编译与实测得到的接口常量 */
object Api {
    /** 业务主入口（AI / solo_hub） */
    const val HOST_AI = "https://trae-api-cn.mchost.guru"

    /** 登录态与 token 交换 */
    const val HOST_LOGIN = "https://api.trae.cn"

    const val PATH_EXCHANGE_TOKEN = "/cloudide/api/v3/trae/oauth/ExchangeToken"
    const val PATH_CLIS = "/api/solo_hub/v1/clis"
    const val PATH_ONBOARDING = "/api/solo_hub/v1/user/onboarding/state"
    const val PATH_MODELS = "/api/remote/v1/models"
    /** 本机 CLI 模型（含用户自建模型），必须带 functions 与 force_refresh */
    const val PATH_CLI_MODELS = "/api/solo_hub/v1/clis/models"
    const val PATH_CONVERSATIONS = "/api/solo_hub/v1/conversations"
    const val PATH_CHAT_SESSIONS = "/api/remote/v1/chat_sessions"
    const val PATH_MSG_ANCHOR = "/api/solo_hub/v1/conversations/messages/anchor"
    const val PATH_FULL_DETAIL = "/api/solo_hub/v1/conversations/full_detail"
    const val PATH_SEND_TASK = "/api/solo_hub/v1/conversations/tasks/create"
    const val PATH_CANCEL_TASK = "/api/solo_hub/v1/conversations/tasks/cancel"
    const val PATH_CONFIRM = "/api/solo_hub/v1/conversations/tasks/confirm"
    const val PATH_USER_DECISION = "/api/solo_hub/v1/conversations/user_decision"
    const val PATH_PIN = "/api/solo_hub/v1/conversations/pin"
    const val PATH_UNPIN = "/api/solo_hub/v1/conversations/unpin"
    const val PATH_SEARCH = "/api/solo_hub/v1/conversations/search"

    /** SSE 事件流 */
    fun ssePath(chatSessionId: String, messageId: String = "") =
        "/api/solo_hub/v1/chat_sessions/$chatSessionId/events?messageId=$messageId"

    /** 发消息 */
    fun sendPath(chatSessionId: String) =
        "/api/solo_hub/v1/conversations/$chatSessionId/messages"

    const val CLIENT_ID = "ono9krqynydwx5"
    const val CLIENT_SECRET = "-"
    const val APP_ID = "677332"
    const val APP_VERSION_CODE = "20260310"
}
