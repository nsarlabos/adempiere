<%@ taglib uri="http://www.zkoss.org/dsp/web/core" prefix="c" %><%@ taglib uri="http://www.zkoss.org/dsp/zk/core" prefix="z" %><%@ taglib uri="http://www.zkoss.org/dsp/web/theme" prefix="t" %>.z-slider{background-image:none}.z-slider-center{${t:borderRadius('5px') };cursor:pointer}.z-slider-button{width:20px;height:20px;border:1px solid #58f2d4;${t:borderRadius('4px') };<c:if test="${ zk.ie != 8 }">${t:gradient('ver','#FFFFFF 0%; #FEFEFE 50%; #EFEFEF 100%') };</c:if>position:relative;cursor:pointer}.z-slider-button:active{border-top-color:#838383;border-left-color:#838383;${t:gradient('ver','#b8fea1 0%; #e1fed8 100%') }}.z-slider-button:hover{background:0;filter:progid:DXImageTransform.Microsoft.gradient(enabled=false);background:#e1fed8}.z-slider-horizontal{height:40px}.z-slider-horizontal .z-slider-center{width:100%;height:8px;margin-top:-3px;<c:if test="${ zk.ie != 8 }">${t:gradient('ver','rgba(157, 157, 157, 0.5) 0%; rgba(130, 140, 149, 0.5) 13%; rgba(215, 215, 215, 0.5) 100%') };</c:if>position:relative;top:50%}.z-slider-horizontal .z-slider-button{top:-5px;left:0}.z-slider-vertical{font-size:0;width:40px;margin-right:0;line-height:0}.z-slider-vertical .z-slider-button{left:-5px}.z-slider-vertical .z-slider-center{width:8px;height:100%;margin:auto;<c:if test="${ zk.ie != 8 }">${t:gradient('hor','rgba(157, 157, 157, 0.5) 0%; rgba(130, 140, 149, 0.5) 13%; rgba(215, 215, 215, 0.5) 100%') };</c:if>}.z-slider-popup{font-family:Arial,Sans-serif;font-size:14px;font-weight:normal;padding:2px;text-shadow:0 1px #fff;${t:boxShadow('0 0 10px rgba(0, 0, 0, 0.35)') }}.z-slider-sphere .z-slider-vertical .z-slider-button{bottom:0}.z-slider-sphere .z-slider-button,.z-slider-scale .z-slider-button{${t:borderRadius('15px') }}.z-slider-scale{background-image:url(${c:encodeThemeURL('~./zul/img/slider/ticks.gif')})}.ie8 .z-slider-button{background:#f7f7f7}.ie8 .z-slider-vertical .z-slider-center,.ie8 .z-slider-horizontal .z-slider-center{background:#bababa}