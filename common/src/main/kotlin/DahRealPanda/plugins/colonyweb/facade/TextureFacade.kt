package DahRealPanda.plugins.colonyweb.facade

import DahRealPanda.plugins.colonyweb.service.TextureService

class TextureFacade(private val textureService: TextureService) {
    fun getTexture(key: String): ByteArray? {
        return textureService.getPng(key)
    }
}
