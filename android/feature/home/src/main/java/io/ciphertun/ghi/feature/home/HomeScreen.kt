package io.ciphertun.ghi.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.ciphertun.ghi.core.designsystem.*
import io.ciphertun.ghi.core.ui.components.GhiScreenScaffold

@Composable
fun HomeScreen(domainCount:Int,status:String,onOpenDiscover:()->Unit,onOpenCountries:()->Unit,onOpenDomains:()->Unit,onOpenSearch:()->Unit,viewModel:HomeViewModel=hiltViewModel()){
 val corePing by viewModel.corePingResult.collectAsState(); val connected=corePing?.contains("alive")==true
 GhiScreenScaffold("Home"){modifier->LazyColumn(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=GhiInk800),modifier=Modifier.fillMaxWidth()){Column(Modifier.background(Brush.linearGradient(listOf(GhiNavy700,GhiInk800))).padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Welcome to GHI PRO",style=MaterialTheme.typography.labelLarge,color=GhiAccentBlue);Text("Global Host Intelligence",style=MaterialTheme.typography.headlineMedium);Text("Discover • Analyze • Intelligence",color=MaterialTheme.colorScheme.onSurfaceVariant);Row(verticalAlignment=Alignment.CenterVertically){Text(if (connected) "● Embedded Go engine ready" else "○ Embedded Go engine unavailable", color = if (connected) GhiAccentBlue else GhiSignalRed)}}}}
  item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){StatCard("🌐 Domains",domainCount.toString(),Modifier.weight(1f));StatCard("🔭 Status",status,Modifier.weight(1f))}}
  item{Text("Quick Actions",style=MaterialTheme.typography.titleLarge)}
  item{ActionCard("🔭 Start Discovery","Select a country and discover public domains with multiple passive sources.",Icons.Filled.Explore,onOpenDiscover)}
  item{ActionCard("🔎 Search Intelligence","Search the domains already cached on this device.",Icons.Filled.Search,onOpenSearch)}
  item{ActionCard("🌍 Countries","Browse discovery results grouped by country-code scope.",Icons.Filled.Public,onOpenCountries)}
  item{ActionCard("🌐 Domains","Open the persistent local discovery cache.",Icons.Filled.Language,onOpenDomains)}
 }}
}
@Composable private fun StatCard(label:String,value:String,modifier:Modifier){Card(modifier,shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=GhiInk800)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(label);Text(value,style=MaterialTheme.typography.titleLarge,color=GhiAccentBlue)}}}
@Composable private fun ActionCard(label:String,description:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){Card(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=GhiInk800)){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){Icon(icon,null,tint=GhiAccentBlue);Column(Modifier.weight(1f)){Text(label,style=MaterialTheme.typography.titleMedium);Text(description,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
