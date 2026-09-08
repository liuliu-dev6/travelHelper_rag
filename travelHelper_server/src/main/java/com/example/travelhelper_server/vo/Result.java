package com.example.travelhelper_server.vo;


import lombok.Data;

@Data
public class Result<T> {
    private Boolean success;
    private Integer code;
    private String message;
    private T data;
    private String error;
    private String rawResponse;
   /* public Boolean getSuccess(){
        return success;
    }
    public void setSuccess(Boolean success){
        this.success = success;
    }
    */
    public static <T> Result<T> ok(){
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setCode(200);
        return result;
    }
    public static <T> Result<T> ok(T data){
        Result<T> result = ok();
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail(){
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(500);
        result.setMessage("失败");
        return result;
    }
    public static <T> Result<T> fail(Integer code,String message){
        Result<T> result = fail();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> error(String error,String rawResponse){
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setError(error);
        result.setRawResponse(rawResponse);
        return result;
    }

}
