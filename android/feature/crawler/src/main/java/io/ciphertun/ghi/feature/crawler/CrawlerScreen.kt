package io.ciphertun.ghi.feature.crawler

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ciphertun.ghi.core.designsystem.*
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

@Composable
fun CrawlerScreen(jobId:String,status:String,domains:List<String>,sources:List<String>,sourceErrors:Map<String,String>,error:String?,elapsedMs:Long,onStop:()->Unit,onOpenDomain:(String)->Unit,onBack:()->Unit){
 GhiScreenScaffold("Live Discovery",onBack){modifier->LazyColumn(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  item{Text("Job ${jobId.take(8)}",style=MaterialTheme.typography.titleMedium);Text(status,color=if(status=="FAILED")GhiSignalRed else GhiAccentBlue)}
  item{if(status=="RUNNING")LinearProgressIndicator(modifier=Modifier.fillMaxWidth()) else LinearProgressIndicator(progress={1f},modifier=Modifier.fillMaxWidth())}
  item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("Discovered",domains.size.toString(),Modifier.weight(1f));Metric("Elapsed","${elapsedMs} ms",Modifier.weight(1f));Metric("Sources",sources.size.toString(),Modifier.weight(1f))}}
  if(status=="RUNNING")item{OutlinedButton(onClick=onStop,modifier=Modifier.fillMaxWidth()){Icon(Icons.Filled.Stop,null);Spacer(Modifier.width(8.dp));Text("STOP")}}
  if(sources.isNotEmpty())item{Text("Active sources",style=MaterialTheme.typography.titleMedium);Text(sources.joinToString("  •  "),color=GhiAccentBlue)}
  sourceErrors.forEach{(name,msg)->item{Text("$name: $msg",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}}
  error?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}}
  item{Text("Live results (${domains.size})",style=MaterialTheme.typography.titleLarge)}
  items(domains.take(200),key={it}){host->ListItem(headlineContent={Text(host)},supportingContent={Text("Passive discovery")},modifier=Modifier.fillMaxWidth())}
  if(domains.isEmpty()&&status!="RUNNING")item{Text("No domains were returned by the selected sources.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
 }}
}
@Composable private fun Metric(label:String,value:String,modifier:Modifier){Card(modifier,colors=CardDefaults.cardColors(containerColor=GhiInk800)){Column(Modifier.padding(14.dp)){Text(value,style=MaterialTheme.typography.titleMedium);Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
