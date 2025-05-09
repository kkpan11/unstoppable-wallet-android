package io.horizontalsystems.bankwallet.core.managers

import com.google.gson.*
import io.horizontalsystems.bankwallet.core.App
import io.horizontalsystems.bankwallet.entities.Guide
import io.horizontalsystems.bankwallet.entities.GuideCategoryMultiLang
import io.reactivex.Single
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.reflect.Type
import java.net.URL
import java.util.*

object GuidesManager {

    private val eduUrl = App.appConfigProvider.eduUrl

    private val gson = GsonBuilder()
            .setDateFormat("yyyy-MM-dd")
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(Guide::class.java, GuideDeserializer(eduUrl))
            .create()

    fun getGuideCategories(): Single<Array<GuideCategoryMultiLang>> {
        return Single.fromCallable {
            val request = Request.Builder()
                    .url(eduUrl)
                    .build()

            val response = OkHttpClient().newCall(request).execute()
            val categories = gson.fromJson(response.body?.charStream(), Array<GuideCategoryMultiLang>::class.java)
            response.close()

            categories
        }
    }

    class GuideDeserializer(guidesUrl: String) : JsonDeserializer<Guide> {
        private val guidesUrlObj = URL(guidesUrl)

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Guide {
            val jsonObject = json.asJsonObject

            return Guide(
                jsonObject.get("title").asString,
                absolutify(jsonObject.get("markdown").asString)
            )
        }

        private fun absolutify(relativeUrl: String?): String {
            return URL(guidesUrlObj, relativeUrl).toString()
        }
    }
}
