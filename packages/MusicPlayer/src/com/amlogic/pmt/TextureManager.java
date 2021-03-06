package com.amlogic.pmt;

import java.io.File;
import java.nio.Buffer;
import java.util.ArrayList;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;

import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11Ext;

import com.amlogic.graphics.DecoderInfo;
import com.amlogic.graphics.PictureKit;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import android.opengl.GLUtils;
import android.opengl.ETC1Util;
import android.util.Log;

public class TextureManager {
	private TextureManager(){}
	
	private static class TextureItem{
		Bitmap bmp = null;
//		public String fname = null;
		int txtID = -1;
		int ref = 0;
		
		public TextureItem(Bitmap bitmap, int texID){
			bmp = bitmap;
			txtID = texID;
			ref = 1;
		}
	};
	
	private static final String TEXMGR_DATA_DIR = "/data/skya3d";
	private static final String TAG = "TextureManager";
	private static ArrayList<TextureItem> texList = new ArrayList<TextureItem>();
//	private static ArrayList<Integer> deledTexList = new ArrayList();
	static GL10 mGL = null;
	
	private static TextureItem findTexture(Bitmap bitmap){
		for(TextureItem ti : texList){
			if(ti.bmp == bitmap)
				return ti;
		}
		return null;
	}
	
	private static TextureItem findTexture(int texID){
		for(TextureItem ti : texList){
			if(ti.txtID == texID)
				return ti;
		}
		return null;
	}	
	
	static int loadThumbTextureFile(GL10 gl, String fileName){
		Log.i("TextureManager", "--> loadThumbTextureFile(" + fileName + ")");
		File fl = new File(fileName);
		if(!fl.exists()){
			Log.i("TextureManager", "<-- loadThumbTextureFile(" + fileName + ") -1 (file not exist)");
			return -1;
		}
		Bitmap bmp = MiscUtil.getThumbImage(fileName, 140, 196);
		if(bmp == null){
			Log.i("TextureManager", "<-- loadThumbTextureFile(" + fileName + ") -1 (bmp==null)");
			return -1;
		}
		int texID = loadTexture(gl, bmp, false);
		bmp.recycle();
		Log.i("TextureManager", "<-- loadThumbTextureFile(" + fileName + ") " + texID);
		return texID;
	}
	
	static int loadTextureFile(GL10 gl, String fileName){
		Log.i("TextureManager", "--> loadTextureFile(" + fileName + ")");
		Bitmap bmp = null;
		File fl = new File(fileName);
		if(!fl.exists()){
			Log.i("TextureManager", "<-- loadTextureFile(" + fileName + ") -1 (file not exist)");
			return -1;
		}
		
		String PlayFormat[] = {"jpg","jpeg"};
		if(MiscUtil.checkEndsWithInStrings(fileName,PlayFormat))
		{
			DecoderInfo di = new DecoderInfo();
			di.widthToDecoder = 1920;
			di.heightToDecoder = 1080;
			di.decodemode = 1;
			bmp = PictureKit.loadPicture(fileName,di);
		}
		Log.d(TAG,"bmp"+bmp);
		if(bmp == null){	//software decode.
			int[] widthHeight = new int[]{0, 0};
			MiscUtil.getBitmapFileWidthHeight(fileName, widthHeight);
			int nw = widthHeight[0] / 1920;
			int ny = widthHeight[1] / 1080;
			
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = nw < ny ? ny : nw;
			Log.d(TAG,"options.inSampleSize:"+options.inSampleSize);
			bmp = BitmapFactory.decodeFile(fileName, options);
		}else{
			Log.i("TextureManager", "-- loadTextureFile(" + fileName + ") hw " + bmp.getWidth() + ", " + bmp.getHeight());
		}
		
		if(bmp == null){
			Log.i("TextureManager", "<-- loadTextureFile(" + fileName + ") -1 (bmp==null)");
			return -1;
		}
		
		//make to 1920*1080.
		int width = bmp.getWidth();
		int height = bmp.getHeight();
		if(width != 1920 || height != 1080){
			if(width * 1080 < height * 1920){	//scale to 1920*?
				int newWidth = width > 1920 ? 1920 : width;
				height = height * newWidth / width;
				width = newWidth;
			}else{
				int newHeight = height > 1080 ? 1080 : height;
				width = width * newHeight / height;
				height = newHeight;
			}
			Bitmap thbmp = MiscUtil.getThumbImage(bmp, width, height);
			Bitmap crtbmp = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
			MiscUtil.drawBitmapIcon(crtbmp, thbmp, (1920-width)/2, (1080-height)/2);
			thbmp.recycle();
			bmp.recycle();
			bmp = crtbmp;
		}
		
		if(bmp == null){
			Log.i("TextureManager", "<-- loadTextureFile(" + fileName + ") -1 (bmp==null)");
			return -1;
		}
		
		int texID = loadTexture(gl, bmp, false);
		bmp.recycle();
		Log.i("TextureManager", "<-- loadTextureFile(" + fileName + ") " + texID + " (pixelsbb)");
		return texID;
	}
//	static int loadTexture(GL10 gl, Bitmap bitmap){
//		return loadTexture(gl, bitmap, true);
//	}
	public static int loadTexture(GL10 gl, Bitmap bitmap, boolean share){
		Log.i("TextureManager", "--> loadTexture(Bitmap)");
		if(bitmap == null){
			Log.i("TextureManager", "<-- loadTexture(Bitmap) -1 (bitmap==null)");
			return -1;
		}
		if(share){
			TextureItem ti;
			synchronized(texList){
				ti = findTexture(bitmap);
				if(ti != null){
					ti.ref++;
					Log.i("TextureManager", "<-- loadTexture(Bitmap) " + ti.txtID + " (ref = " + ti.ref + ")");
					return ti.txtID;
				}
			}
		}
		//avoid exception only.
		if(bitmap.getWidth() > 1920 || bitmap.getHeight() > 1080)
			bitmap = MiscUtil.getThumbImage(bitmap, 1920, 1080);
		//
		int texID = -1;
		if(bitmap.getConfig() == Bitmap.Config.ARGB_8888){
			ByteBuffer pixelsbb;
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			pixelsbb = ByteBuffer.allocateDirect(width*height * 4);
			pixelsbb.order(ByteOrder.nativeOrder());
			bitmap.copyPixelsToBuffer(pixelsbb);
			pixelsbb.position(0);		
				
			texID = loadTexture(gl, width, height, pixelsbb);
		}else{
			int[] tmp_tex = new int[1];
			mGL = gl;
			gl.glEnable(GL10.GL_TEXTURE_2D);
			gl.glGenTextures(1, tmp_tex, 0);
			texID = tmp_tex[0];
			if(texID > 0){
				gl.glBindTexture(GL10.GL_TEXTURE_2D, texID);
				gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
				gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
				if(gl instanceof GL11) {
					gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
					
					GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
				}
				gl.glDisable(GL10.GL_TEXTURE_2D);
				synchronized(texList){texList.add(new TextureItem(share?bitmap:null, texID));}
			}else{
				texID = -1;
			}
		}
		Log.i("TextureManager", "<-- loadTexture(Bitmap) "+texID);
		return texID;
	}

	static int loadTexture(GL10 gl, int w, int h, Buffer data){
		Log.i("TextureManager", "--> loadTexture(Buffer)");
		int[] tmp_tex = new int[1];
		mGL = gl;
		gl.glEnable(GL10.GL_TEXTURE_2D);
		gl.glGenTextures(1, tmp_tex, 0);
		int texID = tmp_tex[0];
		if(texID > 0){
			gl.glBindTexture(GL10.GL_TEXTURE_2D, texID);
			gl.glTexImage2D(GL10.GL_TEXTURE_2D, 0, GL10.GL_RGBA, 
					w, h, 0, GL10.GL_RGBA,
					GL10.GL_UNSIGNED_BYTE, data);
			gl.glTexParameterf(GL10.GL_TEXTURE_2D,
					GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
			gl.glTexParameterf(GL10.GL_TEXTURE_2D,
					GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
			gl.glDisable(GL10.GL_TEXTURE_2D);
			synchronized(texList){texList.add(new TextureItem(null, texID));}
		}else{
			texID = -1;
		}
		Log.i("TextureManager", "<-- loadTexture(Buffer) "+texID);
		return texID;		
	}

	static int loadThumbTextureFileOES(GL10 gl, String fileName){
		Log.i("TextureManager", "--> loadThumbTextureFileOES(" + fileName + ")");
		int texID = loadTextureOES(gl, BitmapFactory.decodeFile(fileName), false);
		Log.i("TextureManager", "<-- loadThumbTextureFileOES(" + fileName + ") "+texID);
		return texID;
	}
	
	static int loadTextureFileOES(GL10 gl, String fileName){
		Log.i("TextureManager", "--> loadTextureFileOES(" + fileName + ")");
		int texID = loadTextureOES(gl, BitmapFactory.decodeFile(fileName), false);
		Log.i("TextureManager", "<-- loadTextureFileOES(" + fileName + ") "+texID);
		return texID;
	}
	static int loadTextureOES(GL10 gl, Bitmap bitmap, boolean share){
		Log.i("TextureManager", "--> loadTextureOES(Bitmap)");
		if(bitmap == null){
			Log.i("TextureManager", "<-- loadTextureOES(Bitmap) -1 (bitmap == null)");
			return -1;
		}
		if(share){
			TextureItem ti;
			synchronized(texList){
				ti = findTexture(bitmap);
				if(ti != null){
					ti.ref++;
					Log.i("TextureManager", "<-- loadTextureOES(Bitmap) " + ti.txtID + " (ref = " + ti.ref + ")");
					return ti.txtID;
				}
			}
		}
		int texID = -1;
		if(bitmap.getConfig() == Bitmap.Config.ARGB_8888){
			ByteBuffer pixelsbb;
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			pixelsbb = ByteBuffer.allocateDirect(width*height * 4);
			pixelsbb.order(ByteOrder.nativeOrder());
			bitmap.copyPixelsToBuffer(pixelsbb);
			pixelsbb.position(0);		
				
			texID = loadTextureOES(gl, width, height, pixelsbb, GL10.GL_RGBA);
		}else{
			int[] tmp_tex = new int[1];
			int[] bmpRect = new int[4];
			bmpRect[0] = 0;
			bmpRect[1] = bitmap.getHeight();
			bmpRect[2] = bitmap.getWidth();
			bmpRect[3] = -bitmap.getHeight();
			mGL = gl;
			gl.glEnable(GL10.GL_TEXTURE_2D);
			gl.glGenTextures(1, tmp_tex, 0);
			texID = tmp_tex[0];
			if(texID > 0){
				gl.glBindTexture(GL10.GL_TEXTURE_2D, texID);
				gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
				gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
				if(gl instanceof GL11) {
					gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
					GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
				}
		        ((GL11) gl).glTexParameteriv(GL10.GL_TEXTURE_2D, 
		                GL11Ext.GL_TEXTURE_CROP_RECT_OES, bmpRect, 0);
				gl.glDisable(GL10.GL_TEXTURE_2D);
				synchronized(texList){texList.add(new TextureItem(share?bitmap:null, texID));}
			}else{
				texID = -1;
			}
		}
		Log.i("TextureManager", "<-- loadTextureOES(Bitmap) "+texID);
		return texID;
	}

	static int loadTextureOES(GL10 gl, int w, int h, Buffer data, int format){
		Log.i("TextureManager", "--> loadTextureOES(Buffer)");
		int[] tmp_tex = new int[1];
		mGL = gl;
		gl.glEnable(GL10.GL_TEXTURE_2D);
		gl.glGenTextures(1, tmp_tex, 0);
		int texID = tmp_tex[0];
		int[] bmpRect = new int[4];
		bmpRect[0] = 0;
		bmpRect[1] = h;
		bmpRect[2] = w;
		bmpRect[3] = -h;
		gl.glBindTexture(GL10.GL_TEXTURE_2D, texID);
		gl.glTexImage2D(GL10.GL_TEXTURE_2D, 0, format, 
				w, h, 0, format,
				GL10.GL_UNSIGNED_BYTE, data);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D,
				GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D,
				GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        ((GL11) gl).glTexParameteriv(GL10.GL_TEXTURE_2D, 
                GL11Ext.GL_TEXTURE_CROP_RECT_OES, bmpRect, 0);
		gl.glDisable(GL10.GL_TEXTURE_2D);
		synchronized(texList){texList.add(new TextureItem(null, texID));}
		Log.i("TextureManager", "<-- loadTextureOES(Buffer) "+texID);
		return texID;		
	}

	/******************************************************************************/
	private static ByteBuffer saveTextureToFile(Bitmap bmp, int width, int height, File f) {
		ByteBuffer pixelsbb;
		int[] pixels = new int[width * height];
		bmp.getPixels(pixels, 0, width, 0, 0, width, height);
		//Log.i("TextureManager", "saveTex  width/height=" + width + "/" + height);
		int c1, c2;
		for (int i = 0; i < (width * height); i++) {
			c1 = pixels[i]&0x00ff00ff;
			c2 = pixels[i]&0xff00ff00;
			c2 |= c1>>16;
			c2 |= c1<<16;
			pixels[i] = c2;
		}
		pixelsbb = ByteBuffer.allocateDirect(width * height * 4);
		pixelsbb.order(ByteOrder.nativeOrder());
		pixelsbb.asIntBuffer().put(pixels);
		pixelsbb.position(0);

		try {
			f.createNewFile();
			FileChannel wChannel = new FileOutputStream(f, false).getChannel();
			ByteBuffer bb = ByteBuffer.allocate(3*4);
			bb.order(ByteOrder.nativeOrder());
			IntBuffer header = bb.asIntBuffer();
			header.put(width);
			header.put(height);
			header.put(pixels.length * 4);
			header.position(0);
			bb.position(0);
			//Log.i("TextureManager", "saveTex  pixels.len=" + pixels.length * 4);;
			wChannel.write(bb);
			wChannel.write(pixelsbb);
			wChannel.close();
			pixelsbb.position(0);
			return pixelsbb;
		} catch (Exception e) {
			Log.i("TextureManager", "saveTex  " + e);
			f.delete();
			return null;
		}
	}

	private static ByteBuffer loadTextureFromFile(File f, int[] resultWidthHeight) {
		ByteBuffer pixelsbb;
		try {
			FileChannel rChannel = new FileInputStream(f).getChannel();
			ByteBuffer bb = ByteBuffer.allocate(3*4);
			bb.order(ByteOrder.nativeOrder());
			rChannel.read(bb);
			bb.position(0);
			IntBuffer header = bb.asIntBuffer();
			int w = header.get();
			int h = header.get();
			int len = header.get();
			//Log.i("TextureManager", "loadTex  w/h=" + w + "/" + h + "len=" + len);
			resultWidthHeight[0] = w;
			resultWidthHeight[1] = h;
			pixelsbb = ByteBuffer.allocateDirect(len);
			rChannel.read(pixelsbb);
			rChannel.close();
			pixelsbb.position(0);
			return pixelsbb;
		} catch (Exception e) {
			Log.i("TextureManager", "loadTex  " + e);
			f.delete();
			return null;
		}
	}

	/* Load texture using an Android resource ID, ex. R.drawable.sunrise_bg 
	 * This will save/read texture data from /data/fbr/ (TEXMGR_DATA_DIR).
	 */
	static int loadTextureOES(GL10 gl, Resources res, int resid) {
		Log.i("TextureManager", "--> loadTextureOES(Resources = " + res.getResourceEntryName(resid) + ")");
		ByteBuffer pixelsbb = null;
		int width=0, height=0;
		File raw = new File(TEXMGR_DATA_DIR, res.getResourceEntryName(resid) + ".tex");
		if (raw.exists()) {
			int[] resultWidthHeight = { 0, 0 };
			pixelsbb = loadTextureFromFile(raw, resultWidthHeight);
			width = resultWidthHeight[0];
			height = resultWidthHeight[1];
			if (pixelsbb == null) {
				final Bitmap bmp = BitmapFactory.decodeResource(res, resid);
				if (bmp != null) {
					width = bmp.getWidth();
					height = bmp.getHeight();
					pixelsbb = saveTextureToFile(bmp, width, height, raw);
				}
			}
		} else {
			File dir = new File(TEXMGR_DATA_DIR);
			// TODO add mkdir to init.rc!!
			if (!dir.exists()) dir.mkdir();
			final Bitmap bmp = BitmapFactory.decodeResource(res, resid);
			if (bmp != null) {
				width = bmp.getWidth();
				height = bmp.getHeight();
				pixelsbb = saveTextureToFile(bmp, width, height, raw);
			}
		}
		if (pixelsbb != null && width > 0 && height > 0){
			int texID = loadTextureOES(gl, width, height, pixelsbb, GL10.GL_RGBA);
			Log.i("TextureManager", "<-- loadTextureOES(Resources = " + res.getResourceEntryName(resid) + ") " + texID);
			return texID;
		}
		
		Log.i("TextureManager", "<-- loadTextureOES(Resources = " + res.getResourceEntryName(resid) + ") -1 (" + (pixelsbb==null?1:0) + " " + width + " " + height + ")");
		return -1;
	}

    /* Load an ETC1 texture.  Place the texture .pkm in res/raw/ and pass its
     * resid in as the argument.
     */
	static int loadTextureOESETC1(GL10 gl, Resources res, int resid) {
		if (res == null){
			Log.i("TextureManager", ">-- loadTextureOESETC1() -1 (res == null) --<");
			return -1;
		}
		Log.i("TextureManager", "--> loadTextureOESETC1(Resources = " + res.getResourceEntryName(resid) + ")");
		int[] tmp_tex = new int[1];
		int[] rect = new int[4];
		mGL = gl;
		gl.glEnable(GL10.GL_TEXTURE_2D);
		gl.glGenTextures(1, tmp_tex, 0);
		int texID = tmp_tex[0];
		if(texID > 0){
			gl.glBindTexture(GL10.GL_TEXTURE_2D, texID);
			gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
			gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
			if(gl instanceof GL11) {
				gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
			}
			try {
				InputStream input = res.openRawResource(resid);
                ETC1Util.ETC1Texture tex = ETC1Util.createTexture(input);
                rect[0] = 0;
                rect[1] = tex.getHeight();
                rect[2] = tex.getWidth();;
                rect[3] = -tex.getHeight();
				ETC1Util.loadTexture(GL10.GL_TEXTURE_2D, 0, 0,
									 GL10.GL_RGB, GL10.GL_UNSIGNED_BYTE, tex);
				input.close();
			} catch (Exception e) {
				Log.i("TextureManager", "loadTex  " + e);
                gl.glDeleteTextures(1, tmp_tex, 0);
				texID = -1;
			}
            ((GL11) gl).glTexParameteriv(GL10.GL_TEXTURE_2D, 
                    GL11Ext.GL_TEXTURE_CROP_RECT_OES, rect, 0);
			gl.glDisable(GL10.GL_TEXTURE_2D);
			synchronized(texList){texList.add(new TextureItem(null, texID));}
		}else{
			texID = -1;
		}
		Log.i("TextureManager", "<-- loadTextureOESETC1(Resources = " + res.getResourceEntryName(resid) + ") " + texID);
		return texID;
	}
	/******************************************************************************/
	
	static void delTexture(int id){
		synchronized(texList){
			TextureItem ti = findTexture(id);
			if(ti != null){
				ti.ref--;
				Log.i("TextureManager", "delTexture( " + id + " ) (ref = " + ti.ref + ")");
				return;	
			}
		}
		Log.i("TextureManager", "delTexture( " + id + " )  (not found...)");
	}
	
	static void clearAllTextLists(){
		Log.i("TextureManager", "==> clearAllTextLists()");
		synchronized(texList){
			for(TextureItem ti : texList){
				Log.i("TextureManager", "---- " + ti.txtID + " -- " + ti.ref + " ----");
			}
			texList.clear();
		}
		Log.i("TextureManager", "<== clearAllTextLists()");
	}
	
	static void delAllTextures(){
		synchronized(texList){
			for(TextureItem ti : texList){
				ti.ref = 0;
			}
		}
		Log.i("TextureManager", "delAllTextures()");
	}
	
	static void freeDeledTextures(){
		if(mGL == null)
			return;
		synchronized(texList){
			if(texList.size() > 0){
				mGL.glEnable(GL10.GL_TEXTURE_2D);
				int[] tmp_tex = new int[1];
				for(int i = texList.size()-1; i>0; i--){
					TextureItem ti = texList.get(i);
					if(ti.ref <= 0){
						if(ti.txtID > 0){
							tmp_tex[0] = ti.txtID;
							mGL.glDeleteTextures(1, tmp_tex, 0);
							Log.i("TextureManager", "[[-- freeDeledTextures() " + ti.txtID);
						}
						texList.remove(i);
					}
				}
				mGL.glDisable(GL10.GL_TEXTURE_2D);
			}
		}
	}
}


