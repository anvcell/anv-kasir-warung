package id.anv.kasirwarung;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.content.pm.PackageManager;
import android.Manifest;
import android.database.*;
import android.database.sqlite.*;
import android.graphics.Color;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import java.text.NumberFormat;
import java.util.*;

public class MainActivity extends Activity {
    static final int CAMERA_PERMISSION = 501;
    final int BG = Color.rgb(16,20,29), PANEL = Color.rgb(29,36,49), GOLD = Color.rgb(230,184,75), WHITE = Color.WHITE;
    DB db; LinearLayout root, list; EditText search; TextView totalView; ArrayList<Item> cart = new ArrayList<>();
    int total = 0; String pendingBarcode = "";
    static class Item { int id, price, stock; String name, barcode; Item(int i,String n,String b,int p,int s){id=i;name=n;barcode=b;price=p;stock=s;} }
    String rupiah(int n){ return "Rp " + NumberFormat.getInstance(new Locale("id","ID")).format(n); }

    @Override public void onCreate(Bundle b){ super.onCreate(b); getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG); db=new DB(this); home(); }
    TextView text(String s,int size,int color){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setPadding(12,10,12,10); return t; }
    Button button(String s){ Button b=new Button(this); b.setText(s); b.setTextColor(GOLD); b.setAllCaps(false); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL)); return b; }
    void base(String title){ root=new LinearLayout(this); root.setOrientation(1); root.setPadding(16,16,16,12); root.setBackgroundColor(BG); ScrollView sc=new ScrollView(this); sc.setFillViewport(true); sc.addView(root); setContentView(sc); TextView h=text(title,23,GOLD); h.setTypeface(null,1); root.addView(h); }
    void home(){
        base("AnV KASIR WARUNG");
        root.addView(text("Kasir offline • Data tersimpan di HP",13,0xffb8c0cc));
        LinearLayout actions=new LinearLayout(this);
        Button add=button("+ Barang"); add.setOnClickListener(v->productDialog(null,""));
        Button scan=button("Scan Barcode"); scan.setOnClickListener(v->scan());
        actions.addView(add,new LinearLayout.LayoutParams(0,-2,1)); actions.addView(scan,new LinearLayout.LayoutParams(0,-2,1)); root.addView(actions);
        search=new EditText(this); search.setSingleLine(true); search.setHint("Cari nama / barcode barang"); search.setTextColor(WHITE); search.setHintTextColor(0xff9aa4b2); search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GOLD)); root.addView(search);
        list=new LinearLayout(this); list.setOrientation(1); root.addView(list);
        totalView=text("Keranjang: "+rupiah(total),18,GOLD); root.addView(totalView);
        Button pay=button("BAYAR / SELESAI"); pay.setOnClickListener(v->checkout()); root.addView(pay);
        Button report=button("Laporan Penjualan"); report.setOnClickListener(v->report()); root.addView(report);
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int count){showProducts(s.toString());} public void afterTextChanged(android.text.Editable e){}});
        showProducts("");
    }
    void showProducts(String q){
        list.removeAllViews(); Cursor c=db.getReadableDatabase().rawQuery("SELECT id,name,barcode,price,stock FROM products WHERE name LIKE ? OR barcode LIKE ? ORDER BY name",new String[]{"%"+q+"%","%"+q+"%"});
        while(c.moveToNext()){
            Item it=new Item(c.getInt(0),c.getString(1),c.getString(2),c.getInt(3),c.getInt(4));
            LinearLayout row=new LinearLayout(this); row.setOrientation(1); row.setPadding(8,5,8,5); row.setBackgroundColor(PANEL);
            TextView label=text(it.name+"  •  "+rupiah(it.price)+"\nStok: "+it.stock+"   |   "+it.barcode,15,WHITE); row.addView(label,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout buttons=new LinearLayout(this); Button buy=button("Tambah ke keranjang"); buy.setOnClickListener(v->addCart(it)); Button edit=button("Edit"); edit.setOnClickListener(v->productDialog(it,it.barcode));
            buttons.addView(buy,new LinearLayout.LayoutParams(0,-2,2)); buttons.addView(edit,new LinearLayout.LayoutParams(0,-2,1)); row.addView(buttons); list.addView(row);
            View gap=new View(this); gap.setLayoutParams(new LinearLayout.LayoutParams(1,8)); list.addView(gap);
        } c.close();
    }
    void addCart(Item it){
        if(it.stock<1){toast("Stok kosong");return;}
        final EditText qty=new EditText(this); qty.setInputType(2); qty.setText("1");
        new AlertDialog.Builder(this).setTitle("Jumlah: "+it.name).setView(qty).setPositiveButton("Tambah",(d,w)->{
            int parsed=1; try{parsed=Integer.parseInt(qty.getText().toString());}catch(Exception e){} final int quantity=Math.max(1,parsed);
            final EditText p=new EditText(this); p.setInputType(2); p.setText(String.valueOf(it.price));
            new AlertDialog.Builder(this).setTitle("Harga jual per item").setMessage("Boleh ubah harga saat transaksi").setView(p).setPositiveButton("OK",(dd,ww)->{
                int parsedPrice=it.price; try{parsedPrice=Integer.parseInt(p.getText().toString());}catch(Exception e){} final int unitPrice=parsedPrice;
                for(int i=0;i<quantity;i++) cart.add(new Item(it.id,it.name,it.barcode,unitPrice,it.stock));
                total+=unitPrice*quantity; totalView.setText("Keranjang: "+rupiah(total)+" ("+cart.size()+" item)"); toast("Ditambahkan");
            }).setNegativeButton("Batal",null).show();
        }).setNegativeButton("Batal",null).show();
    }
    void productDialog(Item old,String barcode){
        LinearLayout box=new LinearLayout(this); box.setPadding(20,8,20,8); box.setOrientation(1);
        EditText name=new EditText(this); name.setHint("Nama barang"); EditText code=new EditText(this); code.setHint("Barcode (boleh kosong)");
        EditText price=new EditText(this); price.setHint("Harga jual eceran"); price.setInputType(2);
        EditText stock=new EditText(this); stock.setHint("Stok"); stock.setInputType(2);
        EditText wholesale=new EditText(this); wholesale.setHint("Harga grosir (opsional)"); wholesale.setInputType(2);
        box.addView(name); box.addView(code); box.addView(price); box.addView(wholesale); box.addView(stock);
        if(old!=null){name.setText(old.name);code.setText(old.barcode);price.setText(""+old.price);stock.setText(""+old.stock);}
        else code.setText(barcode);
        new AlertDialog.Builder(this).setTitle(old==null?"Tambah Barang":"Ubah Barang").setView(box).setPositiveButton("Simpan",(d,w)->{
            String n=name.getText().toString().trim(), bc=code.getText().toString().trim(); if(n.isEmpty()){toast("Nama barang wajib");return;}
            int p=num(price),s=num(stock),g=num(wholesale);
            SQLiteDatabase x=db.getWritableDatabase();
            if(old==null){ContentValues cv=new ContentValues();cv.put("name",n);cv.put("barcode",bc);cv.put("price",p);cv.put("wholesale",g);cv.put("stock",s);x.insert("products",null,cv);}
            else {ContentValues cv=new ContentValues();cv.put("name",n);cv.put("barcode",bc);cv.put("price",p);cv.put("wholesale",g);cv.put("stock",s);x.update("products",cv,"id=?",new String[]{""+old.id});}
            showProducts(search==null?"":search.getText().toString());
        }).setNegativeButton("Batal",null).show();
    }
    int num(EditText e){try{return Integer.parseInt(e.getText().toString());}catch(Exception ex){return 0;}}
    void scan(){
        if(android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
            return;
        }
        startBarcodeScanner();
    }
    void startBarcodeScanner(){
        try {
            IntentIntegrator in = new IntentIntegrator(this);
            in.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
            in.setPrompt("Arahkan kamera ke barcode barang");
            in.setBeepEnabled(true);
            in.setOrientationLocked(false);
            in.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity.class);
            in.initiateScan();
        } catch (Exception e) {
            toast("Pemindai gagal dibuka: " + e.getClass().getSimpleName());
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==CAMERA_PERMISSION){
            if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED) startBarcodeScanner();
            else toast("Izin kamera diperlukan untuk scan barcode");
        }
    }
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data); IntentResult r=IntentIntegrator.parseActivityResult(req,res,data); if(r!=null && r.getContents()!=null){String code=r.getContents(); Cursor c=db.getReadableDatabase().rawQuery("SELECT id,name,barcode,price,stock FROM products WHERE barcode=?",new String[]{code}); if(c.moveToFirst()){addCart(new Item(c.getInt(0),c.getString(1),c.getString(2),c.getInt(3),c.getInt(4)));}else productDialog(null,code);c.close();}}
    void checkout(){
        if(cart.isEmpty()){toast("Keranjang masih kosong");return;}
        EditText paid=new EditText(this);paid.setInputType(2);paid.setHint("Uang diterima");paid.setText(""+total);
        new AlertDialog.Builder(this).setTitle("Total "+rupiah(total)).setView(paid).setPositiveButton("Simpan Transaksi",(d,w)->{
            int bayar=num(paid);if(bayar<total){toast("Uang kurang");return;}
            SQLiteDatabase x=db.getWritableDatabase();x.beginTransaction();
            try{ContentValues sale=new ContentValues();sale.put("created",System.currentTimeMillis());sale.put("total",total);sale.put("paid",bayar);long sid=x.insert("sales",null,sale);
                HashMap<Integer,Integer> counts=new HashMap<>();for(Item it:cart)counts.put(it.id,counts.containsKey(it.id)?counts.get(it.id)+1:1);
                for(Item it:cart){ContentValues line=new ContentValues();line.put("sale_id",sid);line.put("product_id",it.id);line.put("name",it.name);line.put("price",it.price);x.insert("sale_items",null,line);}
                for(Integer id:counts.keySet())x.execSQL("UPDATE products SET stock=MAX(0,stock-?) WHERE id=?",new Object[]{counts.get(id),id});
                x.setTransactionSuccessful();
            }finally{x.endTransaction();}
            int change=bayar-total;cart.clear();total=0;home();toast("Transaksi tersimpan. Kembalian "+rupiah(change));
        }).setNegativeButton("Batal",null).show();
    }
    void report(){Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*),COALESCE(SUM(total),0) FROM sales WHERE date(created/1000,'unixepoch','localtime')=date('now','localtime')",null);c.moveToFirst();String msg="Transaksi hari ini: "+c.getInt(0)+"\nOmzet hari ini: "+rupiah(c.getInt(1));c.close();new AlertDialog.Builder(this).setTitle("Laporan Harian").setMessage(msg).setPositiveButton("Tutup",null).show();}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    static class DB extends SQLiteOpenHelper{
        DB(Context c){super(c,"anv_warung.db",null,1);}
        public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,barcode TEXT,price INTEGER,wholesale INTEGER,stock INTEGER)");d.execSQL("CREATE TABLE sales(id INTEGER PRIMARY KEY AUTOINCREMENT,created INTEGER,total INTEGER,paid INTEGER)");d.execSQL("CREATE TABLE sale_items(id INTEGER PRIMARY KEY AUTOINCREMENT,sale_id INTEGER,product_id INTEGER,name TEXT,price INTEGER)");}
        public void onUpgrade(SQLiteDatabase d,int a,int b){}
    }
}