
package com.egor.mambopuzzle;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    MamboView game;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(14,24,34));
        getWindow().setNavigationBarColor(Color.rgb(14,24,34));
        game = new MamboView(this);
        setContentView(game);
    }
}

class MamboView extends View {
    static final int EMPTY=-1, GREEN=0, BLUE=1;
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    Random rnd = new Random(1);
    int level=1, n=4, selectedType=GREEN;
    int[][] solution, board;
    ArrayList<Mark> marks = new ArrayList<>();
    Stack<int[][]> undo = new Stack<>();
    RectF r = new RectF();
    float gridX, gridY, cell, gap, toolbarY;
    boolean showSettings=false, showHelp=false, won=false, showErrors=true, showTimer=false, vibration=true;
    long startTime=System.currentTimeMillis();
    SharedPreferences sp;

    MamboView(Context c) {
        super(c);
        sp = c.getSharedPreferences("mambo",0);
        level = sp.getInt("level",1);
        text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        newLevel(level);
        setFocusable(true);
    }

    int sizeFor(int lv) {
        if (lv<=100) return 4;
        if (lv<=300) return 6;
        if (lv<=600) return 8;
        if (lv<=850) return 10;
        return 12;
    }

    void newLevel(int lv) {
        if (lv<1) lv=1; if (lv>1000) lv=1000;
        level=lv; n=sizeFor(lv);
        solution=makeSolution(n, lv*9173L+11);
        board=new int[n][n];
        for(int i=0;i<n;i++) Arrays.fill(board[i], EMPTY);
        marks.clear(); undo.clear(); won=false;
        int clues = Math.max(n, (int)(n*n*(0.18 + Math.min(0.16, lv/6000f))));
        Random rr=new Random(lv*1771L+7);
        for(int k=0;k<clues;k++){ int i=rr.nextInt(n), j=rr.nextInt(n); board[i][j]=solution[i][j]; }
        int markCount=Math.max(3, n + lv/80);
        for(int k=0;k<markCount;k++){
            int i=rr.nextInt(n), j=rr.nextInt(n), dir=rr.nextBoolean()?0:1;
            int ni=i+(dir==1?1:0), nj=j+(dir==0?1:0);
            if(ni<n && nj<n) marks.add(new Mark(i,j,ni,nj, solution[i][j]==solution[ni][nj]));
        }
        startTime=System.currentTimeMillis();
        invalidate();
    }

    int[][] makeSolution(int n, long seed) {
        Random rr=new Random(seed);
        ArrayList<int[]> rows = new ArrayList<>();
        int[] row = new int[n];
        genRows(rows,row,0,n);
        Collections.shuffle(rows, rr);
        int[][] s=new int[n][n];
        boolean[] used=new boolean[rows.size()];
        if(!backtrackRows(0,n,rows,used,s)) {
            for(int i=0;i<n;i++) for(int j=0;j<n;j++) s[i][j]=(i+j)%2;
        }
        for(int k=0;k<rr.nextInt(n);k++) {
            int a=rr.nextInt(n), b=rr.nextInt(n);
            int[] tmp=s[a]; s[a]=s[b]; s[b]=tmp;
        }
        return s;
    }

    void genRows(ArrayList<int[]> rows, int[] row, int pos, int n) {
        if(pos==n){
            int c=0; for(int v:row)c+=v;
            if(c==n/2) rows.add(row.clone());
            return;
        }
        for(int v=0;v<=1;v++){
            if(pos>=2 && row[pos-1]==v && row[pos-2]==v) continue;
            row[pos]=v; genRows(rows,row,pos+1,n);
        }
    }

    boolean backtrackRows(int r0,int n,ArrayList<int[]> rows,boolean[] used,int[][] s){
        if(r0==n) return colsOk(s,n,true);
        for(int idx=0;idx<rows.size();idx++) if(!used[idx]){
            s[r0]=rows.get(idx).clone();
            if(!colsOk(s,n,false,r0)) continue;
            used[idx]=true;
            if(backtrackRows(r0+1,n,rows,used,s)) return true;
            used[idx]=false;
        }
        return false;
    }
    boolean colsOk(int[][] s,int n,boolean full){return colsOk(s,n,full,n-1);}
    boolean colsOk(int[][] s,int n,boolean full,int lastRow){
        for(int c=0;c<n;c++){
            int ones=0;
            for(int r0=0;r0<=lastRow;r0++) {
                if(s[r0][c]==1) ones++;
                if(r0>=2 && s[r0][c]==s[r0-1][c] && s[r0][c]==s[r0-2][c]) return false;
            }
            if(ones>n/2) return false;
            if(full && ones!=n/2) return false;
        }
        return true;
    }

    void saveUndo(){
        int[][] cp=new int[n][n];
        for(int i=0;i<n;i++) cp[i]=board[i].clone();
        undo.push(cp);
        if(undo.size()>50) undo.remove(0);
    }
    boolean isFixed(int i,int j){
        // В этой демо-сборке подсказки можно менять, чтобы игрок мог экспериментировать.
        return false;
    }
    void cycleCell(int i,int j){
        if(i<0||j<0||i>=n||j>=n||isFixed(i,j)) return;
        saveUndo();
        board[i][j] = (board[i][j]==EMPTY) ? GREEN : (board[i][j]==GREEN ? BLUE : EMPTY);
        checkWin(); invalidate();
    }
    void eraseCell(int i,int j){ if(i<0||j<0||i>=n||j>=n) return; saveUndo(); board[i][j]=EMPTY; invalidate(); }
    void checkWin(){
        for(int i=0;i<n;i++) for(int j=0;j<n;j++) if(board[i][j]!=solution[i][j]) return;
        won=true; sp.edit().putInt("level",Math.min(1000,level+1)).apply();
    }

    @Override protected void onDraw(Canvas c){
        int W=getWidth(), H=getHeight();
        drawBg(c,W,H);
        if(showSettings){ drawSettings(c,W,H); return; }
        if(showHelp){ drawHelp(c,W,H); return; }
        drawGame(c,W,H);
        if(won) drawWin(c,W,H);
    }

    void drawBg(Canvas c,int W,int H){
        Paint bg=new Paint();
        LinearGradient g=new LinearGradient(0,0,W,H, Color.rgb(10,18,28), Color.rgb(16,30,43), Shader.TileMode.CLAMP);
        bg.setShader(g); c.drawRect(0,0,W,H,bg); bg.setShader(null);
    }

    void drawGame(Canvas c,int W,int H){
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(Color.WHITE); text.setTextSize(dp(30));
        p.setColor(Color.WHITE); p.setStrokeWidth(dp(4)); p.setStyle(Paint.Style.STROKE);
        drawBack(c, dp(42), dp(54));
        text.setTextSize(dp(29));
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(213,142,230)); c.drawCircle(W/2-dp(85), dp(56), dp(13), p);
        c.drawText("Мамбо "+level, W/2+dp(15), dp(66), text);
        p.setStyle(Paint.Style.STROKE); p.setColor(Color.WHITE); p.setStrokeWidth(dp(3));
        c.drawRoundRect(W-dp(116), dp(38), W-dp(82), dp(72), dp(8), dp(8), p); text.setTextSize(dp(21)); c.drawText("?", W-dp(99), dp(64), text);
        c.drawCircle(W-dp(42), dp(55), dp(18), p); c.drawCircle(W-dp(42), dp(55), dp(8), p);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(45,55,66)); round(c, W-dp(76), dp(91), W-dp(24), dp(143), dp(13)); text.setTextSize(dp(30)); c.drawText("»", W-dp(50), dp(127), text);

        float availW=W-dp(54); cell=Math.min((availW-(n-1)*dp(8))/n, dp(72));
        gap=Math.max(dp(5), cell*0.11f);
        gridX=(W-(n*cell+(n-1)*gap))/2;
        gridY=dp(n>=10?210:250);
        if(n>=10) gridY=dp(190);
        for(int i=0;i<n;i++) for(int j=0;j<n;j++) drawCell(c,i,j,board[i][j], false);
        for(Mark m: marks) drawMark(c,m);
        drawToolbar(c,W,H);
    }

    void drawCell(Canvas c,int i,int j,int val, boolean sample){
        float x=gridX+j*(cell+gap), y=gridY+i*(cell+gap);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(51,59,67)); round(c,x,y,x+cell,y+cell,cell*.22f);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(3)); p.setColor(Color.rgb(76,86,99)); round(c,x+dp(1),y+dp(1),x+cell-dp(1),y+cell-dp(1),cell*.20f);
        if(val==EMPTY) return;
        int col = val==GREEN ? Color.rgb(177,221,168) : Color.rgb(144,176,229);
        p.setStyle(Paint.Style.FILL); p.setColor(col); round(c,x+dp(2),y+dp(2),x+cell-dp(2),y+cell-dp(2),cell*.2f);
        p.setColor(Color.argb(50,255,255,255));
        Path diag=new Path(); diag.moveTo(x+cell,y+dp(4)); diag.lineTo(x+cell,y+cell*.55f); diag.lineTo(x+cell*.55f,y+cell); diag.lineTo(x+dp(4),y+cell); diag.close(); c.drawPath(diag,p);
        p.setColor(Color.rgb(17,24,31)); p.setStrokeWidth(dp(3)); p.setStyle(Paint.Style.STROKE);
        if(val==GREEN) round(c,x+cell*.34f,y+cell*.34f,x+cell*.66f,y+cell*.66f,dp(5));
        else { p.setStyle(Paint.Style.FILL); c.drawCircle(x+cell/2,y+cell/2,cell*.22f,p); }
    }
    void drawMark(Canvas c, Mark m){
        float x1=gridX+m.c1*(cell+gap)+cell/2, y1=gridY+m.r1*(cell+gap)+cell/2;
        float x2=gridX+m.c2*(cell+gap)+cell/2, y2=gridY+m.r2*(cell+gap)+cell/2;
        float x=(x1+x2)/2, y=(y1+y2)/2;
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(235,241,247)); c.drawCircle(x,y,cell*.18f,p);
        text.setTextAlign(Paint.Align.CENTER); text.setColor(Color.rgb(15,23,30)); text.setTextSize(cell*.31f);
        c.drawText(m.same?"=":"×", x, y+cell*.1f, text);
    }

    void drawToolbar(Canvas c,int W,int H){
        toolbarY=H-dp(96); float bw=(W-dp(70))/4, bh=dp(72);
        String[] icons={"⌫","↶","✦","💡"};
        for(int k=0;k<4;k++){
            float x=dp(18)+k*(bw+dp(11));
            p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(34,43,53)); round(c,x,toolbarY,x+bw,toolbarY+bh,dp(10));
            text.setTextAlign(Paint.Align.CENTER); text.setColor(Color.rgb(220,230,240)); text.setTextSize(k==3?dp(30):dp(35));
            c.drawText(icons[k], x+bw/2, toolbarY+dp(48), text);
            if(k==2){ badge(c,x+bw-dp(16),toolbarY-dp(8),"1"); }
            if(k==3){ badge(c,x+bw-dp(16),toolbarY-dp(8),"+"); }
        }
    }
    void badge(Canvas c,float x,float y,String s){
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(50,61,72)); round(c,x,y,x+dp(44),y+dp(34),dp(5));
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(Color.WHITE); round(c,x,y,x+dp(44),y+dp(34),dp(5));
        text.setTextSize(dp(21)); text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER); c.drawText(s,x+dp(22),y+dp(24),text);
    }

    void drawHelp(Canvas c,int W,int H){
        text.setTextAlign(Paint.Align.CENTER); text.setColor(Color.WHITE); text.setTextSize(dp(30));
        c.drawText("Обучение Мамбо", W/2, dp(82), text); text.setTextSize(dp(52)); c.drawText("×", W-dp(50), dp(72), text);
        text.setTextSize(dp(32)); c.drawText("Нажмите или дважды", W/2, dp(280), text); c.drawText("нажмите, чтобы заполнить", W/2, dp(322), text);
        float oldCell=cell, oldX=gridX, oldY=gridY; cell=dp(90); gridX=W/2-cell/2; gridY=dp(380); drawCell(c,0,0,EMPTY,true);
        gridX=W/2-dp(130); gridY=dp(550); drawCell(c,0,0,GREEN,true); gridX=W/2-dp(25); drawCell(c,0,0,EMPTY,true);
        text.setTextSize(dp(70)); text.setColor(Color.WHITE); c.drawText("✓", W/2+dp(170), dp(615), text);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); c.drawCircle(W/2-dp(18), H-dp(80), dp(8), p); p.setColor(Color.GRAY); c.drawCircle(W/2+dp(28), H-dp(80), dp(8), p);
        cell=oldCell; gridX=oldX; gridY=oldY;
    }
    void drawSettings(Canvas c,int W,int H){
        text.setTextAlign(Paint.Align.CENTER); text.setColor(Color.WHITE); text.setTextSize(dp(36)); c.drawText("Настройки", W/2, dp(85), text);
        text.setTextAlign(Paint.Align.LEFT); text.setTextSize(dp(32)); c.drawText("×", dp(28), dp(70), text);
        text.setTextSize(dp(30)); c.drawText("Внешний вид", dp(24), dp(170), text);
        settingsRow(c, dp(24), dp(205), W-dp(24), dp(72), "Вибрация", vibration, true);
        text.setColor(Color.rgb(185,194,203)); text.setTextSize(dp(18)); c.drawText("Улучшите взаимодействие с помощью мягкой", dp(46), dp(310), text); c.drawText("тактильной отдачи", dp(46), dp(336), text);
        row(c,dp(24),dp(365),W-dp(24),dp(70)); text.setColor(Color.WHITE); text.setTextSize(dp(24)); c.drawText("Тема", dp(46), dp(410), text);
        pill(c,W-dp(300),dp(382),"Тёмная",false); pill(c,W-dp(200),dp(382),"Светлая",false); pill(c,W-dp(100),dp(382),"Системная",true);
        text.setTextSize(dp(30)); text.setColor(Color.WHITE); c.drawText("Игра", dp(24), dp(510), text);
        settingsRow(c, dp(24), dp(545), W-dp(24), dp(72), "Показать таймер", showTimer, true);
        settingsRow(c, dp(24), dp(632), W-dp(24), dp(72), "Показывать ошибки", showErrors, true);
        row(c,dp(24),dp(719),W-dp(24),dp(72)); text.setTextSize(dp(24)); c.drawText("Сбросить уровень", dp(46), dp(764), text); text.setTextAlign(Paint.Align.RIGHT); c.drawText("›", W-dp(42), dp(768), text); text.setTextAlign(Paint.Align.LEFT);
    }
    void settingsRow(Canvas c,float x,float y,float rgt,float h,String label,boolean on,boolean sw){
        row(c,x,y,rgt,h); text.setTextAlign(Paint.Align.LEFT); text.setColor(Color.WHITE); text.setTextSize(dp(24)); c.drawText(label,x+dp(22),y+dp(45),text);
        if(sw) toggle(c,rgt-dp(72),y+dp(20),on);
    }
    void row(Canvas c,float x,float y,float rgt,float h){ p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(32,42,54)); round(c,x,y,rgt,y+h,dp(10)); }
    void pill(Canvas c,float x,float y,String s,boolean active){ p.setStyle(Paint.Style.FILL); p.setColor(active?Color.WHITE:Color.rgb(38,47,58)); round(c,x,y,x+dp(88),y+dp(40),dp(6)); text.setTextAlign(Paint.Align.CENTER); text.setTextSize(dp(15)); text.setColor(active?Color.rgb(20,28,36):Color.WHITE); c.drawText(s,x+dp(44),y+dp(27),text); text.setTextAlign(Paint.Align.LEFT);}
    void toggle(Canvas c,float x,float y,boolean on){ p.setStyle(Paint.Style.FILL); p.setColor(on?Color.WHITE:Color.rgb(130,136,146)); round(c,x,y,x+dp(54),y+dp(30),dp(15)); p.setColor(Color.rgb(15,24,35)); c.drawCircle(x+(on?dp(39):dp(15)),y+dp(15),dp(14),p); }

    void drawWin(Canvas c,int W,int H){
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(175,0,0,0)); c.drawRect(0,0,W,H,p);
        float s=dp(210), x=W/2-s/2, y=H/2-s/2-dp(60);
        p.setColor(Color.rgb(235,244,248)); round(c,x,y,x+s,y+s,dp(36));
        p.setColor(Color.rgb(142,178,230)); r.set(x,y,x+s/2,y+s/2); c.drawRoundRect(r,dp(34),dp(34),p); p.setColor(Color.rgb(176,222,165)); r.set(x+s/2,y+s/2,x+s,y+s); c.drawRoundRect(r,dp(34),dp(34),p);
        p.setColor(Color.rgb(12,20,28)); c.drawCircle(x+s*.27f,y+s*.27f,dp(24),p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(5)); round(c,x+s*.68f,y+s*.68f,x+s*.87f,y+s*.87f,dp(8));
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(3)); p.setColor(Color.rgb(20,30,40)); c.drawLine(x+s/2,y,x+s/2,y+s,p); c.drawLine(x,y+s/2,x+s,y+s/2,p);
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(215,255,255,255)); c.drawCircle(W/2,y+s/2,dp(55),p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(5)); p.setColor(Color.GRAY); c.drawCircle(W/2,y+s/2,dp(55),p);
        text.setTextAlign(Paint.Align.CENTER); text.setTextSize(dp(70)); text.setColor(Color.GRAY); c.drawText("✓",W/2,y+s/2+dp(25),text);
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        if(e.getAction()!=MotionEvent.ACTION_DOWN) return true;
        float x=e.getX(), y=e.getY(); int W=getWidth(), H=getHeight();
        if(showHelp){ if(y<dp(110) && x>W-dp(95)){showHelp=false; invalidate();} return true; }
        if(showSettings){
            if(y<dp(110) && x<dp(95)){showSettings=false; invalidate(); return true;}
            if(y>dp(545)&&y<dp(617)&&x>W-dp(120)){showTimer=!showTimer; invalidate(); return true;}
            if(y>dp(632)&&y<dp(704)&&x>W-dp(120)){showErrors=!showErrors; invalidate(); return true;}
            if(y>dp(719)&&y<dp(791)){ newLevel(1); showSettings=false; return true; }
            return true;
        }
        if(y<dp(100)&&x>W-dp(90)){showSettings=true; invalidate(); return true;}
        if(y<dp(100)&&x>W-dp(150)){showHelp=true; invalidate(); return true;}
        if(y<dp(100)&&x<dp(90)){ if(level>1)newLevel(level-1); return true; }
        if(x>W-dp(90)&&y>dp(85)&&y<dp(160)){ newLevel(Math.min(1000,level+1)); return true; }
        if(y>toolbarY){
            int k=(int)((x-dp(18))/((W-dp(70))/4+dp(11)));
            if(k==1 && !undo.empty()){ board=undo.pop(); invalidate(); }
            if(k==2){ fillOne(); }
            if(k==3){ showHelp=true; invalidate(); }
            return true;
        }
        int j=(int)((x-gridX)/(cell+gap)), i=(int)((y-gridY)/(cell+gap));
        if(i>=0&&i<n&&j>=0&&j<n){
            float cx=gridX+j*(cell+gap), cy=gridY+i*(cell+gap);
            if(x>=cx&&x<=cx+cell&&y>=cy&&y<=cy+cell) cycleCell(i,j);
        }
        return true;
    }
    void fillOne(){ for(int i=0;i<n;i++)for(int j=0;j<n;j++)if(board[i][j]==EMPTY){saveUndo();board[i][j]=solution[i][j];checkWin();invalidate();return;}}
    float dp(float v){ return v*getResources().getDisplayMetrics().density; }
    void round(Canvas c,float a,float b,float d,float e,float rad){ r.set(a,b,d,e); c.drawRoundRect(r,rad,rad,p); }
    void drawBack(Canvas c,float x,float y){ c.drawLine(x+dp(18),y-dp(18),x-dp(10),y,p); c.drawLine(x-dp(10),y,x+dp(18),y+dp(18),p); c.drawLine(x-dp(8),y,x+dp(28),y,p); }
    static class Mark { int r1,c1,r2,c2; boolean same; Mark(int a,int b,int c,int d,boolean s){r1=a;c1=b;r2=c;c2=d;same=s;} }
}
