package cn.jieti.ui

import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.jieti.JietiApp

data class OverlayColor(val name:String,val hex:String)
object OverlayColors {
 val presets=listOf(OverlayColor("竹月","#819A87"),OverlayColor("天青","#7D9DA4"),OverlayColor("月白","#B2BFC0"),OverlayColor("藕荷","#B49BAA"),OverlayColor("秋香","#A9A176"))
 fun valid(hex:String)=Regex("#[a-fA-F0-9]{6}").matches(hex)
 fun parse(hex:String)=Color.parseColor(if(valid(hex))hex else presets.first().hex)
 fun blend(a:Int,b:Int,weight:Float):Int {val w=weight.coerceIn(0f,1f);return Color.argb(Color.alpha(a),(Color.red(a)*(1-w)+Color.red(b)*w).toInt(),(Color.green(a)*(1-w)+Color.green(b)*w).toInt(),(Color.blue(a)*(1-w)+Color.blue(b)*w).toInt())}
}
@Composable fun OverlayColorSettings(app:JietiApp){
 val selected by app.overlayColor.collectAsState();var custom by remember(selected){mutableStateOf(selected)};val valid=OverlayColors.valid(custom)
 Text("悬浮窗配色",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=20.dp,bottom=8.dp))
 fun apply(hex:String){app.settings.save("overlay.color",hex);app.overlayColor.value=hex}
 Column(verticalArrangement=Arrangement.spacedBy(4.dp)){for(preset in OverlayColors.presets)Row{
  FilterChip(selected.equals(preset.hex,true),{apply(preset.hex)},label={Text(preset.name)},leadingIcon={Surface(color=androidx.compose.ui.graphics.Color(OverlayColors.parse(preset.hex)),shape=RoundedCornerShape(20.dp),modifier=Modifier.size(18.dp)){}})
 }}
 OutlinedTextField(custom,{custom=it},Modifier.fillMaxWidth(),label={Text("自定色值 #RRGGBB")},singleLine=true,isError=!valid,supportingText={if(!valid)Text("请输入 # 加 6 位十六进制色值")})
 if(valid){val tint=androidx.compose.ui.graphics.Color(OverlayColors.parse(custom));Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(28.dp),modifier=Modifier.fillMaxWidth().padding(vertical=8.dp).liquidSurface(tint=tint)){Row(Modifier.padding(20.dp)){Text("玻璃配色预览",Modifier.weight(1f));Text("轻提示 · ↑",color=MaterialTheme.colorScheme.onSurface)}}}
 TextButton(enabled=valid,onClick={apply(custom.uppercase())}){Text("应用自定配色")}
}
