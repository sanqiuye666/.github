package cn.dreamingfish.dispatch.server.model;

/** 只允许固定提示，禁止把底层响应、凭据或配置值拼到异常中。 */
public final class RemoteFailure extends RuntimeException {
    private final String code;
    public RemoteFailure(String code,String message) {super(message);this.code=code;}
    public String code(){return code;}
    public int httpStatus(){return switch(code){case "AUTH_FAILED"->401;case "FORBIDDEN"->403;case "NOT_FOUND"->404;case "CONFLICT"->409;case "CONNECT_FAILED"->504;case "SERVER_ERROR"->502;case "FEATURE_DISABLED","REAL_NOT_IMPLEMENTED"->503;default->400;};}
    public static RemoteFailure of(String code){return new RemoteFailure(code,switch(code){
        case "CONNECT_FAILED"->"连接不上或调用已超时";case "AUTH_FAILED"->"身份验证失败，请重新登录";
        case "SERVER_ERROR"->"服务器操作失败，请保留请求编号";case "INVALID_DATA"->"数据格式不符或无法验证";
        case "FORBIDDEN"->"没有执行此操作的权限";case "CONFLICT"->"状态已变化或操作编号重复，请刷新后重试";
        case "FEATURE_DISABLED"->"此能力尚未启用";case "REAL_NOT_IMPLEMENTED"->"真实服务器实现尚未接入，请使用第二步离线演练配置";
        case "NOT_FOUND"->"目标不存在";case "CONFIRMATION_REQUIRED"->"请输入整合包名称确认删除";case "CANCELLED"->"操作已取消";
        default->"请求未完成";});}
}
