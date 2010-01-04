package org.graphstream.ui.j2dviewer
  
import java.awt.{Container, Graphics2D, RenderingHints}
import java.util.ArrayList

import org.graphstream.ui.geom.Point3

import org.graphstream.graph.Element

import org.graphstream.ui2.swingViewer.GraphRenderer
import org.graphstream.ui2.graphicGraph.{GraphicGraph, GraphicElement, StyleGroup, StyleGroupListener}
import org.graphstream.ui2.graphicGraph.stylesheet.Selector

import org.graphstream.ui.j2dviewer.util.{Camera, Selection}

import org.graphstream.ui.j2dviewer.renderer._

import org.graphstream.ScalaGS._

/**
 * 2D renderer using Swing and Java2D to render the graph.
 * 
 * <p>
 * The role of this class is to equip each style group with a specific renderer and
 * to call these renderer to redraw the graph when needed.
 * </p>
 * 
 * <p>
 * A render pass begins by using the camera instance to set up the projection (allows
 * to pass from graph units to pixels, make a rotation a zoom or a translation) and
 * render each style group once for the shadows, and once for the real rendering
 * in Z order.
 * </p>
 * 
 * <p>
 * This class also handles a "selection" object that represents the current selection
 * and renders it.
 * </p>
 */
class J2DGraphRenderer extends GraphRenderer with StyleGroupListener {
// Attribute	
 
	/** Set the view on the view port defined by the metrics. */
	protected val camera = new Camera

	/** The graph to render. */
	protected var graph:GraphicGraph = null
 
	/** The drawing surface. */
	protected var surface:Container = null
 
	/** The current selection. */
	protected val selection = new Selection
 
// Construction
  
  	def open( graph:GraphicGraph, drawingSurface:Container ) {
	  	if( this.graph == null ) {
		  	this.graph   = graph
		  	this.surface = drawingSurface
		  	graph.getStyleGroups.addListener( this )
	  	}
  	}
	
  	def close() {
  		if( graph != null ) {
  			graph.getStyleGroups.removeListener( this )
  			graph   = null
  			surface = null
  		}
  	}
	
// Access

  	def getViewCenter():Point3 = camera.viewCenter

  	def getViewPercent():Float = camera.viewPercent

  	def getViewRotation():Float = camera.viewRotation

  	def getGraphDimension():Float = camera.metrics.diagonal

  	def findNodeOrSpriteAt( x:Float, y:Float ):GraphicElement = camera.findNodeOrSpriteAt( graph, x, y )
 
  	def allNodesOrSpritesIn( x1:Float, y1:Float, x2:Float, y2:Float ):ArrayList[GraphicElement] = camera.allNodesOrSpritesIn( graph, x1, y1, x2, y2 )
  
  	/**
  	 * The rendering surface this renderer uses.
  	 */
   	def renderingSurface:Container = surface
	
    protected def getStyleRenderer( graph:GraphicGraph ):GraphBackgroundRenderer = {
  		if( graph.getStyle.getRenderer( "dr" ) == null )
  			graph.getStyle.addRenderer( "dr", new GraphBackgroundRenderer( graph, graph.getStyle ) )
  		
  		graph.getStyle.getRenderer( "dr" ).asInstanceOf[GraphBackgroundRenderer]
    }
    
    protected def getStyleRenderer( style:StyleGroup ):StyleRenderer = {
  		if( style.getRenderer( "dr" ) == null )
  			style.addRenderer( "dr", StyleRenderer( style, this ) )
    
  		style.getRenderer( "dr" ).asInstanceOf[StyleRenderer]
    }
    
    protected def getStyleRenderer( element:GraphicElement ):StyleRenderer = {
  		getStyleRenderer( element.getStyle )
    }
    
// Command

	def setBounds( minx:Float, miny:Float, minz:Float, maxx:Float, maxy:Float, maxz:Float ) { camera.setBounds( minx, miny, minz, maxx, maxy, maxz ) }
 
  	def resetView() {
  		camera.setAutoFitView( true )
  		camera.viewRotation = 0
  	}
 
  	def setViewCenter( x:Float, y:Float, z:Float ) {
  		camera.setAutoFitView( false )
  		camera.setViewCenter( x, y )
  	}

  	def setViewPercent( percent:Float ) {
  		camera.setAutoFitView( false )
  		camera.viewPercent = percent
  	}

  	def setViewRotation( theta:Float ) { camera.viewRotation = theta }

  	def beginSelectionAt( x:Float, y:Float ) {
  		selection.active = true
  		selection.begins( x, y )
  	}

  	def selectionGrowsAt( x:Float, y:Float ) {
  		selection.grows( x, y )
  	}

  	def endSelectionAt( x:Float, y:Float ) {
  		selection.grows( x, y )
  		selection.active = false
  	}

  	def moveElementAtPx( element:GraphicElement, x:Float, y:Float ) {
  		val p = camera.inverseTransform( x, y )
  		element.move( p.x, p.y, element.getZ )
  	}

// Commands -- Rendering
  
  	def render( g:Graphics2D, width:Int, height:Int ) {
		val sgs = graph.getStyleGroups
	
		setupGraphics( g )
		graph.computeBounds
		camera.setBounds( graph )
		camera.setViewport( width, height );

		getStyleRenderer(graph).render( g, camera, width, height )
  
		camera.pushView( g, graph )
		sgs.shadows.foreach { group =>
		  	val renderer = getStyleRenderer( group )
		  	renderer.renderShadow( g, camera )
		}
  
		sgs.zIndex.foreach { groups =>
		  	groups.foreach { group =>
		  	  	if( group.getType != Selector.Type.GRAPH ) {
		  	  		val renderer = getStyleRenderer( group )
		  	  		renderer.render( g, camera )
		  	  	}
		  	}
		}
		camera.popView( g )
  
		if( selection.renderer == null ) selection.renderer = new SelectionRenderer( selection )
		selection.renderer.render( g, camera, width, height )
  	}
   
   protected def setupGraphics( g:Graphics2D ) {
	   g.setRenderingHint( RenderingHints.KEY_STROKE_CONTROL,      RenderingHints.VALUE_STROKE_PURE );
//	   g.setRenderingHint( RenderingHints.KEY_RENDERING,           RenderingHints.VALUE_RENDER_SPEED );
	   
	   if( graph.hasAttribute( "ui.antialias" ) ) {
		   g.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING,   RenderingHints.VALUE_TEXT_ANTIALIAS_ON );
		   g.setRenderingHint( RenderingHints.KEY_ANTIALIASING,        RenderingHints.VALUE_ANTIALIAS_ON );
	   } else {
		   g.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING,   RenderingHints.VALUE_TEXT_ANTIALIAS_OFF );
		   g.setRenderingHint( RenderingHints.KEY_ANTIALIASING,        RenderingHints.VALUE_ANTIALIAS_OFF );
	     
	   }
/*  
 		g.setRenderingHint( RenderingHints.KEY_INTERPOLATION,       RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR );
		g.setRenderingHint( RenderingHints.KEY_COLOR_RENDERING,     RenderingHints.VALUE_COLOR_RENDER_SPEED );
		g.setRenderingHint( RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED );
 * 
    	g.setRenderingHint( RenderingHints.KEY_INTERPOLATION,       RenderingHints.VALUE_INTERPOLATION_BICUBIC );
		g.setRenderingHint( RenderingHints.KEY_COLOR_RENDERING,     RenderingHints.VALUE_COLOR_RENDER_QUALITY );
		g.setRenderingHint( RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY );
*/
   }
  
// Commands -- Style group listener
  
    def elementStyleChanged( element:Element, oldStyle:StyleGroup, style:StyleGroup ) {
    	// XXX The element renderer should be the listener, not this. ... XXX

    	if( oldStyle == null ) {
    		
    	} else if( oldStyle != null ) {
    		val renderer = oldStyle.getRenderer( J2DGraphRenderer.DEFAULT_RENDERER )

	    	if( renderer != null && renderer.isInstanceOf[JComponentRenderer] )
	    		renderer.asInstanceOf[JComponentRenderer].unequipElement( element.asInstanceOf[GraphicElement] )
    	}
    }
}

object J2DGraphRenderer {
	val DEFAULT_RENDERER = "j2d_def_rndr";
}