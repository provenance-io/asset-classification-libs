package io.provenance.classification.asset.localtools.feign

import com.fasterxml.jackson.databind.ObjectMapper
import feign.Feign
import feign.Logger
import feign.Request
import feign.Response
import feign.RetryableException
import feign.Retryer
import feign.codec.ErrorDecoder
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import io.provenance.classification.asset.util.objects.ACObjectMapperUtil

object FeignUtil {
    private val OBJECT_MAPPER by lazy { ACObjectMapperUtil.getObjectMapper() }

    fun getBuilder(mapper: ObjectMapper = OBJECT_MAPPER): Feign.Builder = Feign.builder()
        .options(Request.Options(1000, 60000))
        .logLevel(Logger.Level.BASIC)
        .logger(FeignAppLogger())
        .retryer(Retryer.Default(500, 120000, 10))
        .decode404()
        .encoder(JacksonEncoder(mapper))
        .decoder(JacksonDecoder(mapper))
        .errorDecoder(FeignErrorDecoder())

    private class FeignAppLogger : Logger() {
        override fun log(configKey: String?, format: String?, vararg args: Any?) {
            println(String.format(methodTag(configKey) + format, *args))
        }
    }

    private class FeignErrorDecoder : ErrorDecoder.Default() {
        override fun decode(methodKey: String, response: Response?): Exception = when (response?.status()) {
            502 -> RetryableException("502: Bad Gateway", response.request().httpMethod(), null)
            503 -> RetryableException("503: Service Unavailable", response.request().httpMethod(), null)
            504 -> RetryableException("504: Gateway Timeout", response.request().httpMethod(), null)
            else -> super.decode(methodKey, response)
        }
    }
}
