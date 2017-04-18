import kotlin.concurrent.thread
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.file.Files
import java.nio.file.Paths
import java.io.*
import java.net.URLDecoder
import java.time.LocalDateTime
//import com.fasterxml.jackson.core.JsonProcessingException
//import com.fasterxml.jackson.databind.ObjectMapper
//import com.fasterxml.jackson.module.kotlin.*

//ここからselenium
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.phantomjs.PhantomJSDriver
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.WebDriverWait
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.OutputType
import org.openqa.selenium.Dimension
import java.util.concurrent.TimeUnit
import java.net.URLEncoder


// ここからredis(jedis)
import redis.clients.jedis.Jedis

val url_details:MutableMap<String, String> = mutableMapOf()

fun _writer(url:String, title:String, text:String, outDir:String) {
   val escapeTitle = title.replace("/", "___SLA___")
   val escapeUrl   = url.replace("http://", "")
                        .replace("https://", "")
                        .replace("/", "_")
   var joined = "${escapeUrl}_${escapeTitle}"
   if(joined.length > 50 ) {
     joined = joined.slice(0..50)
   }
   val f = PrintWriter("${outDir}/${joined}")
   f.append(text)
   f.close()
}

fun _save_conf(json:String) { 
  PrintWriter("url_details.json").append(json).close()
}
fun _load_conf():MutableMap<String, String> { 
  try {
    /*val mapper = ObjectMapper().registerKotlinModule()
    val json   = File("url_details.json").readText()
    val url_details = mapper.readValue<MutableMap<String, String>>(json)*/
    return url_details
  } catch( e: java.io.FileNotFoundException ) {
    return mutableMapOf()
  }
}

fun _parser(url:String, outDir:String):Set<String> { 
  var doc:Document
  try { 
    doc = Jsoup.connect(url).timeout(6000).get()
  } catch( e : org.jsoup.HttpStatusException ) {
    println(e)
    return setOf()
  } catch( e : java.lang.IllegalArgumentException ) { 
    println(e)
    return setOf()
  } catch( e : java.net.MalformedURLException ) {
    println(e)
    return setOf()
  } catch( e : java.net.UnknownHostException ) {
    //ホスト不明
    return setOf()
  } catch( e : java.net.SocketException ) { 
    return setOf()
  } catch( e : java.io.IOException ) {
    return setOf()
  }
  if( doc == null || doc.body() == null ) 
    return setOf()
  val btext =  doc.body().text()     
  val title =  doc.title()    
  val urls = doc.select("a[href]").map { link ->
    link.attr("abs:href")
  }
  _writer(url, title, doc.html(), outDir)
  return  urls.toSet()
}

fun widthSearch(args:Array<String>) { 
  val outDir           = args.toList().getOrElse(1) { "out" } 
  val TargetDomain     = args.toList().getOrElse(2) { "http://www.yahoo.co.jp" } 
  val FilteringDomains = File("conf/filterURLs").readText().replace("\n", " ").split(" ").toList()
  _parser(TargetDomain).map { url -> 
    url_details[url] = "まだ"
  }

  for(depth in (0..1000) ) {
    val urls:MutableSet<String> = mutableSetOf()
    val threads = url_details.keys.map { url ->
      val th = Thread { 
        if(url_details[url]!! == "まだ") {
          _parser(url, outDir).map { next ->
            urls.add(next)
          }
          println("終わりに更新 : $url")
          url_details[url] = "終わり"
        }
      }
      th 
    }
    threads.map { th -> 
      th.start()
      while(true) {
        if(Thread.activeCount() > 250 ) {
          println("now sleeping...")
          Thread.sleep(50)
        }else{ break } 
      }
    }
    threads.map { th -> 
      th.join() 
    }
    println("now regenerationg url_index...")
    urls.map { url ->
      if( FilteringDomains.any { f -> url.contains(f) } && url_details.get(url) == null ) {
        url_details[url] = "まだ"
      }
    }
  }
}

fun batchExecutor(args :Array<String>) {
  val filename = args[1]
  val urls = File(filename).readLines().toList()
  urls.map { url -> 
    val decoded = URLDecoder.decode(url)
    println(decoded)
    _parser(decoded)
  }
}

fun imageSeleniumDriver(args: List<String?>) {
  val inputFile = args.getOrElse(1) { "bwh.txt" }
  val outputFile = args.getOrElse(2) { "imgs" } 
  println("このファイルを用います, ${inputFile}")
  println("このディレクトリに保存します, ${outputFile}")
  File("${inputFile}").readLines().map { x -> 
    x.split(" ").first()
  }.map { name ->
    println(name)
    // ディレクトリを作成
    Runtime.getRuntime().exec("mkdir -p ${outputFile}").waitFor()
    // 名前をURLエンコード
    val encoded = URLEncoder.encode(name)
    val driver = PhantomJSDriver()
    driver.manage().window().setSize(Dimension(4096,4160))
    driver.get("https://www.bing.com/images/search?q=${encoded}")
    //すべての画像が描画されるのを待つ
    Thread.sleep(3001)
    val html = driver.getPageSource()
    val doc  = Jsoup.parse(html.toString(), "UTF-8")
    println(doc.title())
    doc.select("img").filter { x -> 
      x.attr("class") == "mimg"
    }.map { x ->
      println(x)
      val data_bm = x.attr("data-bm")
      val src     = x.attr("src")
      Runtime.getRuntime().exec("wget ${src} -O ${outputFile}/${name}_${data_bm}.jpg").waitFor()
    }
    val data = (driver as TakesScreenshot).getScreenshotAs(OutputType.BYTES)
    Files.write(Paths.get("${outputFile}/${name}.png"), data)
    //もう必要ないので、driverをquitする
    driver.quit()
  }
}

fun jedisTest(args: Array<String>) {
  val jedis = Jedis("localhost")
  jedis.keys("*").map { k ->
    try { 
      println(k)
      println(jedis.hgetAll(k)) 
    } catch ( e: redis.clients.jedis.exceptions.JedisDataException ) {
    }
  }
  val m = mapOf("a" to "b", "c" to "d")
  jedis.hmset("key3", m)
}

fun pawooHunterDriver(args:List<String>, mode:Int ){ 
  val num = args.filter { x -> x.contains("th=") }.map { x -> x.split("=").last() }?.last()?.toInt() ?: 3
  val (targetInstance, conf, outputFile) = when(mode) { 
    1 -> Triple("https://pawoo.net", "~/private_configs/pawoo_mail_pass", "pawoo")
    else -> Triple("https://mstdn.jp" , "~/private_configs/mstdnjp_mail_pass", "mstdnjp")
  }
  for( instance in (1..num) ) { 
    thread { pawooHunter(instance, args, targetInstance, conf, outputFile) } 
    Thread.sleep(500)
  }
}

fun main(args: Array<String>) {
  val Mode  = args.toList().getOrElse(0) {  
    println("モードを指定してくだい...")
    System.exit(0)
  }
  when(Mode) { 
    "widthSearch" -> widthSearch(args)
    "batch"       -> batchExecutor(args)
    "image"       -> imageSeleniumDriver(args.toList())
    "jedisTest"   -> jedisTest(args)
    "pawooHunter" -> pawooHunterDriver(args.toList(), 1)
    "mstdnHunter" -> pawooHunterDriver(args.toList(), 2)
  }
}
