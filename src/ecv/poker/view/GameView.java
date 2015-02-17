package ecv.poker.view;

import java.util.ArrayList;
import java.util.List;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.AsyncTask;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;
import ecv.poker.R;
import ecv.poker.activity.SettingsActivity;
import ecv.poker.activity.TitleActivity;
import ecv.poker.card.Card;
import ecv.poker.game.Game;

public class GameView extends View {

	// width:height ratios of bitmaps
	private static final float BUTTON_RATIO = 412f / 162;
	private static final float CARD_RATIO = 222f / 284;
	// padding between cards and buttons
	private static final int PADDING = 10;

	private Context context;
	private MyButton foldButton, checkButton, callButton, betButton,
			raiseButton;
	private Slider slider;
	private Game game;
	private RectF table;
	private Paint greenPaint, whitePaint;
	private int screenW, screenH;
	private int cardW, cardH;
	private int buttonW, buttonH;
	private int compCardsX, compCardsY;
    private int comp2CardsX, comp2CardsY;
	private int playerCardsX, playerCardsY;
	private int communityX, communityY;
	private float loadingProgress;
	private SparseArray<Bitmap> bitmaps;
	private SoundPool soundPool;
	private AudioManager audioManager;
	private boolean audioEnabled;
	private SharedPreferences settings;
	public int shuffleSound, dealSound, chipSound;

	public GameView(Context context) {
		super(context);
		this.context = context;

		settings = context.getSharedPreferences(
				SettingsActivity.class.getName(), Context.MODE_PRIVATE);

		audioEnabled = settings.getBoolean(
				context.getString(R.string.enable_sound), true);
		soundPool = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
		audioManager = (AudioManager) context
				.getSystemService(Context.AUDIO_SERVICE);
		shuffleSound = soundPool.load(context, R.raw.shuffle, 1);
		dealSound = soundPool.load(context, R.raw.deal, 1);
		chipSound = soundPool.load(context, R.raw.chips, 1);
		greenPaint = new Paint();
		greenPaint.setColor(0xff006600);
		greenPaint.setAntiAlias(true);
		whitePaint = new Paint();
		whitePaint.setColor(Color.WHITE);
		whitePaint.setAntiAlias(true);
		whitePaint.setTextSize(32);
		whitePaint.setTextAlign(Align.CENTER);

		foldButton = new MyButton(R.drawable.fold_button_up,
				R.drawable.fold_button_down);
		checkButton = new MyButton(R.drawable.check_button_up,
				R.drawable.check_button_down);
		callButton = new MyButton(R.drawable.call_button_up,
				R.drawable.call_button_down);
		betButton = new MyButton(R.drawable.bet_button_up,
				R.drawable.bet_button_down);
		raiseButton = new MyButton(R.drawable.raise_button_up,
				R.drawable.raise_button_down);

		slider = new Slider();
		slider.setMinVal(0);
		slider.setMaxVal(100);

		table = new RectF();
		game = new Game(this);
	}

	@Override
	public void onSizeChanged(int w, int h, int oldW, int oldH) {
		super.onSizeChanged(w, h, oldW, oldH);
		screenH = h;
		screenW = w;

		// Get dimensions of bitmaps to set scales - fit 5 cards across screen
		cardW = (screenW - 7 * PADDING) / 5;
		cardH = (int) (cardW / CARD_RATIO);

		// set things like cards and chip labels relative to table
		table.set(0, cardH, screenW, cardH + screenH / 2);
		compCardsX = (int) table.left + 50;
		compCardsY = (int) table.top - cardH / 2;
        comp2CardsX = (int) table.right - cardW - 50;
        comp2CardsY = (int) table.top - cardH / 2;
        playerCardsX = (int) table.left + 50;
		playerCardsY = (int) table.bottom - cardH / 2;
		communityX = (int) (table.right + table.left) / 5 - cardW + PADDING / 2;
		communityY = (int) (table.top + table.bottom) / 2 - cardH / 2;

		// slider oriented along bottom, takes up 90% width
		slider.setY(screenH - 100);
		slider.setStartX(screenW / 10);
		slider.setStopX(9 * slider.getStartX());
		slider.setCurX(slider.getStartX());

		// fit 4 buttons across screen
		buttonW = (screenW - 6 * PADDING) / 4;
		buttonH = (int) (buttonW / BUTTON_RATIO);

		betButton.setWidth(buttonW);
		callButton.setWidth(buttonW);
		checkButton.setWidth(buttonW);
		foldButton.setWidth(buttonW);
		raiseButton.setWidth(buttonW);

		betButton.setHeight(buttonH);
		callButton.setHeight(buttonH);
		checkButton.setHeight(buttonH);
		foldButton.setHeight(buttonH);
		raiseButton.setHeight(buttonH);

		// buttons above slider, aligned horizontally
		foldButton.setX(PADDING);
		foldButton.setY(slider.getY() - 2 * buttonH);

		checkButton.setX(foldButton.getX() + buttonW + PADDING);
		checkButton.setY(foldButton.getY());
		callButton.setX(checkButton.getX());
		callButton.setY(checkButton.getY());

		betButton.setX(callButton.getX() + buttonW + PADDING);
		betButton.setY(callButton.getY());
		raiseButton.setX(betButton.getX());
		raiseButton.setY(betButton.getY());

		// Load bitmaps asynchronously on a background thread
		MyBitmap[] bmpsToLoad = new MyBitmap[63];
		int i = 0;
		List<Card> cards = new ArrayList<Card>(game.getDeck());
		cards.addAll(game.getBot().getCards());
        cards.addAll(game.getBot2().getCards());
		cards.addAll(game.getUser().getCards());
		cards.addAll(game.getCommunityCards());
		for (Card c : cards) {
			int resId = getResources().getIdentifier("card" + c.getId(),
					"drawable", "ecv.poker");
			c.setResId(resId);
			bmpsToLoad[i++] = new MyBitmap(resId, cardW, cardH);
		}
		bmpsToLoad[i++] = new MyBitmap(R.drawable.card_back, cardW, cardH);
		bmpsToLoad[i++] = new MyBitmap(R.drawable.bet_button_down, buttonW,
				buttonH);
		bmpsToLoad[i++] = new MyBitmap(R.drawable.call_button_down, buttonW,
				buttonH);
		bmpsToLoad[i++] = new MyBitmap(R.drawable.check_button_down, buttonW,
				buttonH);
		bmpsToLoad[i++] = new MyBitmap(R.drawable.fold_button_down, buttonW,
				buttonH);
		bmpsToLoad[i++] = new MyBitmap(R.drawable.raise_button_down, buttonW,
				buttonH);
		bmpsToLoad[i++] = new MyBitmap(R.drawable.bet_button_up, buttonW,
				buttonH);
		bmpsToLoad[i++] = new MyBitmap(R.drawable.call_button_up, buttonW,
				buttonH);
		bmpsToLoad[i++] = new MyBitmap(R.drawable.check_button_up, buttonW,
				buttonH);
		bmpsToLoad[i++] = new MyBitmap(R.drawable.fold_button_up, buttonW,
				buttonH);
		bmpsToLoad[i++] = new MyBitmap(R.drawable.raise_button_up, buttonW,
				buttonH);
		new BitmapLoader().execute(bmpsToLoad);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		// Bitmaps are still being loaded. Display the progress
		if (bitmaps == null) {
			canvas.drawText(
					"LOADING... " + (int) (loadingProgress * 100) + "%",
					screenW / 2, screenH / 2, whitePaint);
			int startBar = screenW / 4;
			int stopBar = 3 * screenW / 4;
			int barY = (int) (screenH / 2 + whitePaint.getFontSpacing());
			int curBar = (int) ((stopBar - startBar) * loadingProgress + startBar);
			canvas.drawLine(startBar, barY, curBar, barY, whitePaint);
		} else {
			canvas.drawOval(table, greenPaint);
			// draw player, computer, and community cards
			for (int i = 0; i < game.getBot().getCards().size(); i++) {
				int cardResId = R.drawable.card_back;
				// show the user the bot's cards at the end of the hand.
				if (game.isHandOver())
					cardResId = game.getBot().getCards().get(i).getResId();
				drawBitmap(canvas, cardResId, compCardsX + i
						* (cardW + PADDING), compCardsY);
			}
            for (int i = 0; i < game.getBot2().getCards().size(); i++) {
                int cardResId = R.drawable.card_back;
                // show the user the bot's cards at the end of the hand.
                if (game.isHandOver())
                    cardResId = game.getBot2().getCards().get(i).getResId();
                drawBitmap(canvas, cardResId, comp2CardsX - i
                        * (cardW + PADDING), comp2CardsY);
            }
			for (int i = 0; i < game.getUser().getCards().size(); i++) {
				drawBitmap(canvas, game.getUser().getCards().get(i).getResId(),
						playerCardsX + i * (cardW + PADDING), playerCardsY);
			}
			for (int i = 0; i < game.getCommunityCards().size(); i++) {
				drawBitmap(canvas, game.getCommunityCards().get(i).getResId(),
						communityX + i * (cardW + PADDING), communityY);
			}

			// draw chip counts
			canvas.drawText(game.getUser().getChips() + " curBet=" + Integer.toString(game.getUser().getCurBet()), playerCardsX
					+ cardW + PADDING / 2,
					playerCardsY + cardH + whitePaint.getFontSpacing(),
					whitePaint);
			canvas.drawText(game.getBot().getChips() + " curBet=" + Integer.toString(game.getBot().getCurBet()), compCardsX
                    + cardW + PADDING / 2,
                    compCardsY + cardH + whitePaint.getFontSpacing(),
					whitePaint);
            canvas.drawText(game.getBot2().getChips() + " curBet=" + Integer.toString(game.getBot2().getCurBet()), comp2CardsX
                    - PADDING / 2,
                    comp2CardsY + cardH + whitePaint.getFontSpacing(),
                    whitePaint);
			canvas.drawText(game.getPot() + "", (table.left + table.right) / 2,
					communityY + cardH + whitePaint.getFontSpacing(),
					whitePaint);

			// hide buttons when not your turn
			if (((game.getTurn() == 0) && !game.isHandOver()) && !game.getUser().IsFolded()) {
				drawBitmap(canvas, foldButton.getStateResId(),
						foldButton.getX(), foldButton.getY());
				if (game.getCurBet() == 0) {
					checkButton.enable();
					callButton.disable();
					betButton.enable();
					raiseButton.disable();
					drawBitmap(canvas, checkButton.getStateResId(),
							checkButton.getX(), checkButton.getY());
					drawBitmap(canvas, betButton.getStateResId(),
							betButton.getX(), betButton.getY());
				} else {
					checkButton.disable();
					callButton.enable();
					betButton.disable();
					raiseButton.enable();
					drawBitmap(canvas, callButton.getStateResId(),
							callButton.getX(), callButton.getY());
					drawBitmap(canvas, raiseButton.getStateResId(),
							raiseButton.getX(), raiseButton.getY());
				}
				// make sure bet values are in correct range - either match
				// current bet or min of big blind
				if (game.getCurBet() == 0)
					slider.setMinVal(game.getMinBetAllowed());
				else
					slider.setMinVal(game.getCurBet());
				slider.setMaxVal(game.getMaxBetAllowed());
				slider.draw(canvas, whitePaint);

				// draw value of the bet
				int betValX = betButton.getX() + (int) (buttonW * 1.5);
				int betValY = betButton.getY()
						+ (int) whitePaint.getFontSpacing();
				canvas.drawText(slider.getVal() + "", betValX, betValY,
						whitePaint);
			}
		}
	}
	
	public SharedPreferences getSettings() {
		return settings;
	}

	public boolean onTouchEvent(MotionEvent evt) {
		int action = evt.getAction();
		int x = (int) evt.getX();
		int y = (int) evt.getY();

		switch (action) {
		case MotionEvent.ACTION_DOWN:
			foldButton.detectCollision(x, y);
			checkButton.detectCollision(x, y);
			callButton.detectCollision(x, y);
			betButton.detectCollision(x, y);
			raiseButton.detectCollision(x, y);
			slider.detectCollision(x, y);
			if (slider.isPressed())
				slider.setCurX(x);
			break;
		case MotionEvent.ACTION_MOVE:
			if (slider.isPressed())
				slider.setCurX(x);
			break;
		case MotionEvent.ACTION_UP:
			// press anywhere after a hand to start a new one
			if (game.isHandOver()) {
				game.setupHand();
			} else if (game.getUser().IsFolded()){
                game.setNextTurn();
            } else if (foldButton.isPressed()) {
				game.getUser().fold();
				endMyTurn();
			} else if (checkButton.isPressed()) {
				game.getUser().check();
				endMyTurn();
			} else if (callButton.isPressed()) {
				game.getUser().call();
				endMyTurn();
			} else if (betButton.isPressed()) {
				game.getUser().bet(slider.getVal());
				endMyTurn();
			} else if (raiseButton.isPressed()) {
				game.getUser().raise(slider.getVal());
				endMyTurn();
			}

			foldButton.setPressed(false);
			checkButton.setPressed(false);
			callButton.setPressed(false);
			betButton.setPressed(false);
			raiseButton.setPressed(false);
			slider.setPressed(false);
			break;
		}
		invalidate();

		return true;
	}

	private void endMyTurn() {
		game.setNextTurn();
		slider.setCurX(slider.getStartX());
	}

	public void toast(String msg) {
		Toast toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.CENTER, 0, 0);
		toast.show();
	}

	public void playSound(int id) {
		if (audioEnabled) {
			float volume = audioManager
					.getStreamVolume(AudioManager.STREAM_MUSIC);
			soundPool.play(id, volume, volume, 1, 0, 1);
		}
	}

	// look up the ID in the sparsearray (hashmap) and draw it if found
	private void drawBitmap(Canvas canvas, int resId, int x, int y) {
		Bitmap bmp = bitmaps.get(resId);
		if (bmp != null)
			canvas.drawBitmap(bmp, x, y, null);
	}

	public void makeEndGameDialog() {
		final Dialog dialog = new Dialog(context);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.endhand_dialog);
		Button playAgain = (Button) dialog.findViewById(R.id.play_again_button);
		playAgain.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				game.reset();
				dialog.dismiss();
			}
		});

		Button exit = (Button) dialog.findViewById(R.id.exit_button);
		exit.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(context, TitleActivity.class);
				context.startActivity(intent);
				dialog.dismiss();
			}
		});
		dialog.show();
	}

	/**
	 * Load bitmaps in asynchronously
	 * 
	 * @author Evan
	 * 
	 */
	private class BitmapLoader extends
			AsyncTask<MyBitmap, Float, SparseArray<Bitmap>> {

		private float CURRENT_PROGRESS = 0;

		@Override
		protected SparseArray<Bitmap> doInBackground(MyBitmap... params) {
			SparseArray<Bitmap> loaded = new SparseArray<Bitmap>(params.length);
			for (MyBitmap mb : params) {
				Bitmap bmp = BitmapFactory.decodeResource(getResources(),
						mb.getResId());
				bmp = Bitmap.createScaledBitmap(bmp, mb.getWidth(),
						mb.getHeight(), false);
				loaded.put(mb.getResId(), bmp);
				CURRENT_PROGRESS += 1f / params.length;
				publishProgress(CURRENT_PROGRESS);
			}
			return loaded;
		}

		@Override
		protected void onProgressUpdate(Float... progress) {
			loadingProgress = progress[0];
			invalidate();
		}

		@Override
		protected void onPostExecute(SparseArray<Bitmap> result) {
			bitmaps = result;
			game.setupHand();
			invalidate();
		}
	}
}
