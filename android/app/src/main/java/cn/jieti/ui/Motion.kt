package cn.jieti.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SoftEase=CubicBezierEasing(.22f,.7f,.2f,1f)

@Composable fun <T> SoftPage(target:T,modifier:Modifier=Modifier,content:@Composable ColumnScope.(T)->Unit){
 val distance=with(LocalDensity.current){10.dp.roundToPx()}
 AnimatedContent(targetState=target,modifier=modifier,transitionSpec={
  ((fadeIn(tween(260,35,SoftEase))+slideInVertically(tween(290,easing=SoftEase)){distance}) togetherWith
   (fadeOut(tween(110))+slideOutVertically(tween(150,easing=SoftEase)){-distance/2})).using(null)
 },label="reading-page"){value->Column(Modifier.fillMaxSize()){content(value)}}
}

@Composable fun SoftDisclosure(visible:Boolean,content:@Composable ()->Unit){
 AnimatedVisibility(visible=visible,enter=fadeIn(tween(180))+expandVertically(tween(250,easing=SoftEase)),
  exit=fadeOut(tween(100))+shrinkVertically(tween(210,easing=SoftEase)),label="soft-disclosure"){content()}
}

@Composable fun SoftNavigation(title:String,selected:Boolean,onClick:()->Unit){
 val colors=MaterialTheme.colorScheme
 val ink by animateColorAsState(if(selected)colors.primary else colors.onSurfaceVariant,tween(180),label="navigation-ink")
 val fill by animateColorAsState(if(selected)colors.secondaryContainer.copy(alpha=.65f)else Color.Transparent,tween(220),label="navigation-fill")
 TextButton(modifier=if(selected)Modifier.liquidSurface(24f,colors.background==Color(0xFF20241F))else Modifier,onClick=onClick,shape=RoundedCornerShape(24.dp),colors=ButtonDefaults.textButtonColors(contentColor=ink,containerColor=if(selected)fill.copy(alpha=.16f)else Color.Transparent)){
  Text(title,fontSize=13.sp)
 }
}
