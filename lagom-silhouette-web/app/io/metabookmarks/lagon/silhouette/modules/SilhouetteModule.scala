package io.metabookmarks.lagon.silhouette.modules

/**
  * Created by olivier.nouguier@gmail.com on 22/08/2017.
  */

import com.mohiva.play.silhouette.api.actions._
import com.mohiva.play.silhouette.api.crypto.{Crypter, CrypterAuthenticatorEncoder, Signer}
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.services._
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.api.{Environment, EventBus, Silhouette, SilhouetteProvider}
import com.mohiva.play.silhouette.crypto.{JcaCrypter, JcaCrypterSettings, JcaSigner, JcaSignerSettings}
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth1._
import com.mohiva.play.silhouette.impl.providers.oauth1.secrets.{CookieSecretProvider, CookieSecretSettings}
import com.mohiva.play.silhouette.impl.providers.oauth1.services.PlayOAuth1Service
import com.mohiva.play.silhouette.impl.providers.oauth2._
import com.mohiva.play.silhouette.impl.providers.openid.YahooProvider
import com.mohiva.play.silhouette.impl.providers.openid.services.PlayOpenIDService
import com.mohiva.play.silhouette.impl.providers.state.{UserStateItem, UserStateItemHandler}
import com.mohiva.play.silhouette.impl.services._
import com.mohiva.play.silhouette.impl.util._
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.daos.{DelegableAuthInfoDAO, InMemoryAuthInfoDAO}
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository
import com.softwaremill.macwire._
import io.metabookmarks.lagon.silhouette.models.daos.OAuth2InfoDAO
import io.metabookmarks.lagon.silhouette.models.services.LagomIdentityService
import io.metabookmarks.lagon.silhouette.utils.auth.DefaultEnv
import io.metabookmarks.session.api.SessionService
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import play.api.Configuration
import play.api.i18n.{I18nComponents, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.libs.openid.{OpenIdClient, OpenIDComponents, WsDiscovery, WsOpenIdClient}
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.filters.csrf.CSRFComponents


trait SilhouetteModule
  extends I18nComponents
  with SecuredActionComponents
  with UnsecuredActionComponents
  with UserAwareActionComponents
  with OpenIDComponents
  with CSRFComponents
{

  //def configuration: Configuration

  def wsClient: WSClient

  //def openIdClient: OpenIdClient

  def identityService: LagomIdentityService
  def sessionService: SessionService

  lazy val testFormat = Json.format[OAuth2Info]

  lazy val silhouette = wireWith(Silhouette.apply _)

  lazy val passwordInfoDao = new InMemoryAuthInfoDAO[PasswordInfo]
  lazy val oauth1InfoDao = new InMemoryAuthInfoDAO[OAuth1Info]
 // lazy val oauth2InfoDao = new InMemoryAuthInfoDAO[OAuth2Info]
  lazy val oauth2InfoDao = wireWith(OAuth2InfoDAOProvider.apply _)
  lazy val openIdInfoDao = new InMemoryAuthInfoDAO[OpenIDInfo]

  lazy val cookieHeader = new DefaultCookieHeaderEncoding()
  lazy val signerSettings = new JcaSignerSettings("key", "pepper")

  lazy val signer = wire[JcaSigner]
  lazy val crypter = new JcaCrypter(configuration.underlying.as[JcaCrypterSettings]("silhouette.oauth1TokenSecretProvider.crypter"))

  lazy val clock = Clock()
  lazy val eventBus = EventBus()
  lazy val fingerprintGenerator = new DefaultFingerprintGenerator(false)
  lazy val idGenerator = new SecureRandomIDGenerator
  lazy val passwordHasher = new BCryptPasswordHasher

  lazy val passwordHasherRegistry = new PasswordHasherRegistry(passwordHasher)

 // lazy val cache: AsyncCacheApi            = defaultCacheApi
 // lazy val ehCacheAPi = defaultCacheApi
 // lazy val cacheLayer = wire[PlayCacheLayer]
  lazy val authenticatorService = wireWith(SilhouetteAuthenticatorService.apply _)



  lazy val httpLayer = wire[PlayHTTPLayer]
  lazy val silhouetteEnvironment = wireWith(SilhouetteEnvironment.apply _)
  lazy val settings = GravatarServiceSettings()
  lazy val avatarService = wire[GravatarService]
  lazy val tokenSecretProvider = wireWith(SilhouetteOAuth1TokenSecretProvider.apply _)
  lazy val stateProvider = wireWith(SilhouetteSocialStateHandler.apply _)
  lazy val facebookProvider = wireWith(SilhouetteFacebookProvider.apply _)
  lazy val xingProvider = wireWith(SilhouetteXingProvider.apply _)
  lazy val twitterProvider = wireWith(SilhouetteTwitterProvider.apply _)
  lazy val vKProvider = wireWith(SilhouetteVKProvider.apply _)
  lazy val googleProvider = wireWith(SilhouetteGoogleProvider.apply _)
  lazy val yahooProvider = wireWith(SilhouetteYahooProvider.apply _)
  lazy val socialProviderRegistry = wireWith(SilhouetteSocialProviderRegistry.apply _)
  lazy val authInfoRepository = wireWith(SilhouetteAuthInfoRepository.apply _)
  lazy val credentialsProvider: CredentialsProvider = wireWith(SilhouetteCredentialsProvider.apply _)


 // lazy val openIdClient = wire[WsOpenIdClient]

  lazy val  discovery = wire[WsDiscovery]

  object Silhouette {
    def apply(
    env: Environment[DefaultEnv],
    securedAction: SecuredAction,
    unsecuredAction: UnsecuredAction,
    userAwareAction: UserAwareAction
    ): SilhouetteProvider[DefaultEnv] = new SilhouetteProvider[DefaultEnv](env, securedAction, unsecuredAction, userAwareAction)
  }

  object SilhouetteAuthenticatorService {
    def apply(
             signer: Signer,
             cookieHeaderEncoding: CookieHeaderEncoding,
             crypter: Crypter,
               fingerprintGenerator: FingerprintGenerator,
               idGenerator: IDGenerator,
               clock: Clock, configuration: Configuration
             ): AuthenticatorService[CookieAuthenticator] = {
      val encoder = new CrypterAuthenticatorEncoder(crypter)
      val config = configuration.underlying.as[CookieAuthenticatorSettings]("silhouette.authenticator")
      new CookieAuthenticatorService(config, None, signer, cookieHeaderEncoding, encoder, fingerprintGenerator, idGenerator, clock)
    }
  }

  object SilhouetteEnvironment {
    def apply(
               userService: LagomIdentityService,
               authenticatorService: AuthenticatorService[CookieAuthenticator],
               eventBus: EventBus
             ): Environment[DefaultEnv] = {
      Environment[DefaultEnv](userService, authenticatorService, Seq(), eventBus)
    }
  }

  object SilhouetteOAuth1TokenSecretProvider {
    def apply(clock: Clock, configuration: Configuration,  signer: Signer,
              crypter: Crypter): OAuth1TokenSecretProvider = {
      val settings = configuration.underlying.as[CookieSecretSettings]("silhouette.oauth1TokenSecretProvider")
      new CookieSecretProvider(settings, signer, crypter, clock)
    }
  }

  object OAuth2InfoDAOProvider {
    def apply(sessionService: SessionService) = new OAuth2InfoDAO[OAuth2Info](sessionService, Json.format[OAuth2Info])
  }


  object SilhouetteSocialStateHandler {
    def apply(
               idGenerator: IDGenerator,
               signer: Signer,
               configuration: Configuration, clock: Clock): SocialStateHandler = {
      new DefaultSocialStateHandler(Set(new UserStateItemHandler[UserStateItem](new UserStateItem(Map()))), signer)
    }
  }

  object SilhouetteFacebookProvider {
    def apply(
               httpLayer: HTTPLayer, stateProvider: SocialStateHandler, configuration: Configuration
             ): FacebookProvider = {
      val settings = configuration.underlying.as[OAuth2Settings]("silhouette.facebook")
      new FacebookProvider(httpLayer, stateProvider, settings)
    }
  }

  object SilhouetteGoogleProvider {
    def apply(
               httpLayer: HTTPLayer, stateProvider: SocialStateHandler, configuration: Configuration
             ): GoogleProvider = {
      val settings = configuration.underlying.as[OAuth2Settings]("silhouette.google")
      new GoogleProvider(httpLayer, stateProvider, settings)
    }
  }

  object SilhouetteVKProvider {
    def apply(
               httpLayer: HTTPLayer, stateProvider: SocialStateHandler, configuration: Configuration
             ): VKProvider = {
      val settings = configuration.underlying.as[OAuth2Settings]("silhouette.vk")
      new VKProvider(httpLayer, stateProvider, settings)
    }
  }

  object SilhouetteTwitterProvider {
    def apply(
               httpLayer: HTTPLayer, tokenSecretProvider: OAuth1TokenSecretProvider, configuration: Configuration
             ): TwitterProvider = {
      val settings = configuration.underlying.as[OAuth1Settings]("silhouette.twitter")
      new TwitterProvider(httpLayer, new PlayOAuth1Service(settings), tokenSecretProvider, settings)
    }
  }

  object SilhouetteXingProvider {
    def apply(
               httpLayer: HTTPLayer, tokenSecretProvider: OAuth1TokenSecretProvider, configuration: Configuration
             ): XingProvider = {
      val settings = configuration.underlying.as[OAuth1Settings]("silhouette.xing")
      new XingProvider(httpLayer, new PlayOAuth1Service(settings), tokenSecretProvider, settings)
    }
  }

  object SilhouetteYahooProvider {
    def apply(
                httpLayer: HTTPLayer, client: OpenIdClient, configuration: Configuration
             ): YahooProvider = {
      val settings = configuration.underlying.as[OpenIDSettings]("silhouette.yahoo")
      new YahooProvider(httpLayer, new PlayOpenIDService(client, settings), settings)
    }
  }


  object SilhouetteSocialProviderRegistry {
    def apply(
               facebookProvider: FacebookProvider,
               googleProvider: GoogleProvider,
               vkProvider: VKProvider,
               twitterProvider: TwitterProvider,
               xingProvider: XingProvider,
               yahooProvider: YahooProvider
             ): SocialProviderRegistry = {
      SocialProviderRegistry(
        Seq(
          googleProvider, facebookProvider, twitterProvider,
          vkProvider, xingProvider, yahooProvider
        )
      )
    }
  }

  object SilhouetteAuthInfoRepository {
    def apply(
               passwordInfoDAO: DelegableAuthInfoDAO[PasswordInfo],
               oauth1InfoDAO: DelegableAuthInfoDAO[OAuth1Info],
               oauth2InfoDAO: DelegableAuthInfoDAO[OAuth2Info],
               openIDInfoDAO: DelegableAuthInfoDAO[OpenIDInfo]
             ): AuthInfoRepository = {
      new DelegableAuthInfoRepository(
        passwordInfoDAO, oauth1InfoDAO, oauth2InfoDAO, openIDInfoDAO
      )
    }
  }

  object SilhouetteCredentialsProvider {
    def apply(
               authInfoRepository: AuthInfoRepository,
               passwordHasherRegistry: PasswordHasherRegistry
             ): CredentialsProvider = {
      new CredentialsProvider(authInfoRepository, passwordHasherRegistry)
    }
  }

}