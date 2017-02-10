/*
The MIT License (MIT)

Copyright (c) 2017 Pierre Lindenbaum

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

*/
package com.github.lindenb.jvarkit.tools.vcfviewgui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import javax.script.CompiledScript;
import javax.script.SimpleBindings;

import com.github.lindenb.jvarkit.tools.vcfviewgui.chart.AlleleFrequencyChartFactory;
import com.github.lindenb.jvarkit.tools.vcfviewgui.chart.ChartFactory;
import com.github.lindenb.jvarkit.tools.vcfviewgui.chart.GenotypeTypeChartFactory;
import com.github.lindenb.jvarkit.tools.vcfviewgui.chart.TiTvChartFactory;
import com.github.lindenb.jvarkit.tools.vcfviewgui.chart.VariantContextChartFactory;
import com.github.lindenb.jvarkit.tools.vcfviewgui.chart.VariantDepthChartFactory;
import com.github.lindenb.jvarkit.tools.vcfviewgui.chart.VariantQualChartFactory;
import com.github.lindenb.jvarkit.tools.vcfviewgui.chart.VariantTypeChartFactory;
import com.github.lindenb.jvarkit.util.Counter;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeType;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFFilterHeaderLine;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.WindowEvent;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.Callback;

public class VcfStage extends NgsStage {
    private static final int DEFAULT_VCF_RECORDS_COUNT=Integer.parseInt(System.getProperty("jxf.ngs.default.vcf", "1000"));
    static final ExtensionFilter EXTENSION_FILTER=new ExtensionFilter("Variant Files", ".vcf",".vcf.gz");
    private static final String SPINNER_VALUE_KEY="vcf.spinner.value";
    
    /** variant oriented chart-factories */
    private static final List<Supplier<ChartFactory<VariantContext>>> VARIANT_CHART_FACTORIES= Arrays.asList(
		()->new VariantTypeChartFactory(),
		()->new GenotypeTypeChartFactory(),
		()->new AlleleFrequencyChartFactory(),
		()->new VariantQualChartFactory(),
		()->new TiTvChartFactory(),
		()->new VariantDepthChartFactory()
		);

    
    private class VcfQualityStage
		extends AbstractQualityStage<VariantContext>
			{
    		private class ScanVcfThread extends ScanThread
    			{
    			ScanVcfThread(final ChartFactory<VariantContext> factory,final File source,CompiledScript compiledScript,final Predicate<VariantContext> otherFilters)
    				{
    				super(factory,source,compiledScript,otherFilters);
    				}
        		        		
    			@Override
    			public void run() {
    				VCFFileReader vcfReader =null;
    				CloseableIterator<VariantContext> iter=null;
    				try 
	    				{
    						final SimpleBindings bindings=new SimpleBindings();
	    					vcfReader = new VCFFileReader(this.source,false);
	    					bindings.put("header", vcfReader.getFileHeader());
	    					iter = vcfReader.iterator();
	    					
	    					while(!kill_flag && iter.hasNext())
	    						{
	    						final VariantContext ctx=iter.next();
	    						
	    						nItems++;
	    						if(this.compiledScript!=null )
	    							{
	    							bindings.put("variant", ctx);
	    							if(!accept(bindings)) continue;
	    							}
	    						if(!super.otherFilters.test(ctx)) continue;
	    						super.factory.visit(ctx);
	    						update();
	    						}
	    					iter.close();
	    					vcfReader.close();
	    				
	    					atEnd();
	    				
	    				}
    				catch(final Throwable err)
    					{
    					super.onError(err);
    					}
    				finally
    					{
    					CloserUtil.close(iter);
    					CloserUtil.close(vcfReader);
    					}
    				}
    			
    			}
    		
    		VcfQualityStage(final ChartFactory<VariantContext> factory,final File file,final CompiledScript compiledScript,final Predicate<VariantContext> otherFilters)
	    		{
    			super(factory,file,compiledScript,otherFilters);
	    		}

			@Override
			protected AbstractQualityStage<VariantContext>.ScanThread createThread(
					final ChartFactory<VariantContext> factory,
					File file,
					CompiledScript compiledScript,
					final Predicate<VariantContext> otherFilters
					) {
				return new ScanVcfThread(factory,file, compiledScript,otherFilters);
				}
    		
			}


    
	/** display item in VCF/INFO */
    private static class InfoTableRow
		{
		final String key;
		final Integer index;
		final Object value;
		InfoTableRow(final String key,final Integer index,final Object value)
			{
			this.key = key;
			this.index = index;
			this.value=value;
			}
		boolean accept(final Pattern pat)
			{
			if(pat==null) return true;
			if(pat.matcher(key).find()) return true;
			if(value!=null && pat.matcher(String.valueOf(value)).find()) return true;
			return false;
			}
		}
    
	private final TextField javascriptFILTERfield=new TextField("");
	private final VCFFileReader vcfFileReader;
	private final TableView<VariantContext> variantTable;
	private final TableView<Genotype> genotypeTable;
	private final TableView<InfoTableRow> infoTableRow;
	private final TableView<String> filterTableRow;
	private final BorderPane genotypeChartPane;
	private final CheckBox cboxShowHomRef=new CheckBox("HomRef");
	private final CheckBox cboxShowNoCall=new CheckBox("NoCall");
	private final TextField tfFilterInfo=new TextField();
	
	VcfStage(final JfxNgs owner,final File path) throws IOException {
		super(owner,path);
		this.vcfFileReader = new VCFFileReader(path,true);
		final VCFHeader header=this.vcfFileReader.getFileHeader();
		
        
      
        
        //selectFlagMenu.getItems().addAll(flag2filterOutMenuItem.values());
        final VBox vbox1 = new VBox();
        vbox1.getChildren().add(super.menuBar);
        
        FlowPane top1= new FlowPane();
        vbox1.getChildren().add(top1);
        top1.getChildren().add(new Label("GoTo:"));
        top1.getChildren().add(super.gotoField);
        final Button gotoButton=new Button("Go");
        gotoButton.setOnAction(new EventHandler<ActionEvent>()
			{
			@Override
			public void handle(ActionEvent event)
				{
				reloadData();
				}
			});
        top1.getChildren().add(gotoButton);
        top1.getChildren().add(new Separator(Orientation.VERTICAL));
        top1.getChildren().add(new Label("Limit:"));
        
        super.maxItemsLimitSpinner.setValueFactory(
        		new SpinnerValueFactory.IntegerSpinnerValueFactory(0,100000,
        				owner.preferences.getInt(SPINNER_VALUE_KEY, DEFAULT_VCF_RECORDS_COUNT)
        				));;

        top1.getChildren().add(super.maxItemsLimitSpinner);
        top1.getChildren().add(new Separator(Orientation.VERTICAL));
		
        final Button igvButton =new Button("IGV");
        top1.getChildren().add(igvButton);
        igvButton.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent event) {
					openInIgv();
				}
			});
        this.gotoField.setOnAction(new EventHandler<ActionEvent>()
			{
			@Override
			public void handle(ActionEvent event)
				{
				reloadData();
				}
			});
		final TabPane tabPane=new TabPane();
		tabPane.setPadding(new Insets(10, 10, 10, 10));
		vbox1.getChildren().add(tabPane);
		
		
		/*GridPane gridPane = new GridPane();
		gridPane.setPadding(new Insets(10, 10, 10, 10));
		gridPane.setVgap(4);
		gridPane.setHgap(4);*/

        
		final SplitPane split1=new SplitPane();
		split1.setOrientation(Orientation.HORIZONTAL);
		
		/* build variant table */
		this.variantTable = this.buildVariantTable();
		split1.getItems().add(this.variantTable);
		
		/* build genotype table */
		
		this.genotypeTable =this.buildGenotypeTableRow(header);
		FlowPane flow3 = new FlowPane(this.cboxShowHomRef,this.cboxShowNoCall);
		this.cboxShowHomRef.setSelected(true);
		this.cboxShowNoCall.setSelected(true);
		EventHandler<ActionEvent> repaintGTTable=new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				refreshGenotypeTable(variantTable.getSelectionModel().getSelectedItem());
				}
			};
		this.cboxShowHomRef.setOnAction(repaintGTTable);
		this.cboxShowNoCall.setOnAction(repaintGTTable);

		BorderPane pane2=new BorderPane(this.genotypeTable);
		pane2.setTop(flow3);
		//GridPane.setConstraints(pane2,5, 0,5,14); 
		split1.getItems().add(pane2);
		
		final SplitPane split2=new SplitPane();
		split2.setOrientation(Orientation.HORIZONTAL);

		/* filter table */
		this.filterTableRow = this.buildFilterTable();
		//GridPane.setConstraints( this.filterTableRow,0, 10,3,1);
		//gridPane.getChildren().add(this.filterTableRow);
		split2.getItems().add(this.filterTableRow);
		/* genotype pane */
		
		
		this.genotypeChartPane = new BorderPane(this.makeGenotypePie(null));
		this.genotypeChartPane.setPadding(new Insets(5));
		//GridPane.setConstraints( this.genotypeChartPane,0, 12,3,4);
    	//gridPane.getChildren().add(this.genotypeChartPane);
		split2.getItems().add(this.genotypeChartPane);
		
		
		/* build info Table table */
		this.infoTableRow = this.buildInfoTableRow();
		flow3 = new FlowPane(new Label("Filter:"),tfFilterInfo);
		tfFilterInfo.setPrefColumnCount(10);
		tfFilterInfo.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				reloadInfoTable(variantTable.getSelectionModel().getSelectedItem());
			}
		});
		pane2=new BorderPane(this.infoTableRow);
		pane2.setTop(flow3);
		//GridPane.setConstraints( this.infoTableRow,3, 10,8,5); // column=3 row=1
		//gridPane.getChildren().add(this.infoTableRow);
		split2.getItems().add(pane2);
				
       
		
        
        //vbox1.getChildren().add(gridPane);
		final SplitPane split3=new SplitPane();
		split3.setOrientation(Orientation.VERTICAL);
		split3.getItems().addAll(split1,split2);

		final Tab tab=new Tab("Variants", split3);
		tab.setClosable(false);
		tabPane.getTabs().add(tab);
		tabPane.getTabs().add(buildInfoHeaderTab(header));
		tabPane.getTabs().add(buildFormatHeaderTab(header));
		tabPane.getTabs().add(buildFilterHeaderTab(header));
        tabPane.getTabs().add(buildDictTab( header.getSequenceDictionary()));
        tabPane.getTabs().add(buildJavascriptPane());
        /* register selection handler */
        /* when a read is selected update the flagsTable and metaDataTable */
        this.variantTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            fireSelectedVariantChanged(newSelection);
        	});
        
        
        final FlowPane bottom=new FlowPane(super.messageLabel);
        vbox1.getChildren().add(bottom);
        final Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();

        final Scene scene = new Scene(vbox1,
        		primaryScreenBounds.getWidth()-200,
        		primaryScreenBounds.getHeight()-200);
       
        this.setScene(scene);
        
       
        fileMenu.getItems().addAll(
        		menuForSavingTable("Variants",this.variantTable),
        		menuForSavingTable("INFO",this.infoTableRow),
        		menuForSavingTable("FILTER",this.filterTableRow),
        		menuForSavingTable("Genotype",this.genotypeTable)
        		);
        
        /* fill stats menu */
        final Supplier<List<VariantContext>> variantsProvider=()->this.variantTable.getItems();
        
        for(final Supplier<ChartFactory<VariantContext>> supplier: VARIANT_CHART_FACTORIES)
	        {
        	final ChartFactory<VariantContext> factory = supplier.get();
        	final MenuItem menuItem=new MenuItem("Local "+factory.getName());
        	menuItem.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent event) {
					doMenuShowLocalStats(factory, variantsProvider);
				}
			});
        	statsMenu.getItems().add(menuItem);
	        }
        super.statsMenu.getItems().add(new SeparatorMenuItem());
        for(final Supplier<ChartFactory<VariantContext>> supplier: VARIANT_CHART_FACTORIES)
	        {
	    	final ChartFactory<VariantContext> factory = supplier.get();
	    	final MenuItem menuItem=new MenuItem("Whole"+factory.getName());
	    	menuItem.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent event) {
					doMenuShowWholeStats(factory);
				}
			});
	    	statsMenu.getItems().add(menuItem);
	        }

        
        this.addEventHandler(
    			WindowEvent.WINDOW_HIDING ,new EventHandler<WindowEvent>() {
                    @Override
                    public void handle(final WindowEvent event) {
                    owner.preferences.putInt(SPINNER_VALUE_KEY,maxItemsLimitSpinner.getValue().intValue());
                    }
                });

		}
	
    @Override
    protected SAMSequenceDictionary getSAMSequenceDictionary() {
    	return this.vcfFileReader.getFileHeader().getSequenceDictionary();
    	}

	
	protected void doMenuSaveAs()
		{
		final FileChooser fc= owner.newFileChooser();
    	fc.setSelectedExtensionFilter(EXTENSION_FILTER);
		final File saveAs= owner.updateLastDir(fc.showSaveDialog(this));
		if(saveAs==null) return;
		if(saveAs.equals(super.urlOrFile)) return;
		if(!saveAs.getName().endsWith(".vcf.gz"))
			{
			final Alert alert=new Alert(AlertType.ERROR, "Output should end with .vcf.gz", ButtonType.OK);
			alert.showAndWait();
			return;
			}
		
    	CompiledScript compiledScript=null;
    	SimpleBindings binding=null;
    	if(this.owner.javascriptEngine!=null && !this.javascriptArea.getText().trim().isEmpty())
    		{
    		try
    			{
    			binding=new SimpleBindings();
    			compiledScript =this.owner.javascriptEngine.compile(this.javascriptArea.getText());
    			binding.put("header", this.vcfFileReader.getFileHeader());
    			}
    		catch(final Exception err)
    			{
    			JfxNgs.showExceptionDialog(this, err);
    			return;
    			}
    		}

		
		VariantContextWriter w=null;
		CloseableIterator<VariantContext> iter=null;
		try
			{
			final VariantContextWriterBuilder vcwb=new VariantContextWriterBuilder();
			vcwb.setOutputFile(saveAs);
			w= vcwb.build();
			VCFHeader header2= new VCFHeader(this.vcfFileReader.getFileHeader());
        	final String filterName = this.javascriptFILTERfield.getText().trim();
        	if(!filterName.isEmpty())
        		{
        		header2.addMetaDataLine(new VCFFilterHeaderLine(filterName, "Set by User in JfxNgs"));
        		}
			w.writeHeader(header2);
			iter=this.vcfFileReader.iterator();
			while(iter.hasNext())
				{
				VariantContext ctx=iter.next();
				if(compiledScript!=null)
        			{
        			binding.put("variant", ctx);
        			if(!super.accept(compiledScript,binding))
        				{
        				if(filterName.isEmpty()) continue;
        				ctx=new VariantContextBuilder(ctx).filter(filterName).make();
        				}
        			}
				w.add(ctx);
				}
			w.close();
			}
		catch(Exception err)
			{
			JfxNgs.showExceptionDialog(this, err);
			return;
			}
		finally
			{
			CloserUtil.close(w);
			}    		
		}
	
	private Tab buildJavascriptPane()
		{
		final ScrollPane scroll=new ScrollPane(super.javascriptArea);
		scroll.setFitToWidth(true);
		scroll.setFitToHeight(true);
		final BorderPane pane=new BorderPane(scroll);
		if(this.owner.javascriptEngine!=null) {
    		final HBox flowPane = new HBox(
    				new Label("set following FILTER on rejection:"),
    				javascriptFILTERfield
    				);
    		
    		this.javascriptFILTERfield.setPromptText("If not empty , don't discard the variant but set the FILTER");
    		flowPane.getChildren().addAll(super.makeJavascriptButtons());
    		pane.setTop(flowPane);
    		}
		final Label helpLabel=new Label("The script injects:\n"+
				"* header ( htsjdk.variant.vcf.VCFHeader )\n"+
				"* variant ( htsjdk.variant.variantcontext.VariantContext )\n"+
				"The script should return a boolean: true (accept variant) or false (reject variant)"
				);
		helpLabel.setWrapText(true);
		pane.setBottom(helpLabel);
		
		final Tab tab=new Tab(JAVASCRIPT_TAB_KEY,pane);
		tab.setClosable(false);
		return tab;
		}
	
	/** build FILTER table */
	private TableView<String> buildFilterTable()
		{
		final TableView<String> table=new TableView<>();
		
		TableColumn<String,String>  scol = new TableColumn<>("Filter");
		scol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<String,String>, ObservableValue<String>>() {				
			@Override
			public ObservableValue<String> call(CellDataFeatures<String, String> param) {
				return new ReadOnlyObjectWrapper<String>(param.getValue());
				}
			});
        table.getColumns().add(scol);
		
        
		return table;    
		}
	
	@Override
	void openInIgv() {
    	final VariantContext ctx=this.variantTable.getSelectionModel().getSelectedItem();
    	if(ctx==null) {
    		updateStatusBar(AlertType.WARNING,"no variant selected");
    		return;
    		}
    	openInIgv(ctx);
    	}
	
	
	/** build table of variants */
	private TableView<VariantContext> buildVariantTable()
		{
		final TableView<VariantContext> table=new TableView<>();
		table.getColumns().add(makeColumn("CHROM", V->V.getContig()));
        table.getColumns().add(makeColumn("POS", V->V.getStart()));
        table.getColumns().add(makeColumn("ID", V->V.hasID()?V.getID():null));
        table.getColumns().add(makeColumn("REF", V->V.getReference().getDisplayString()));
        table.getColumns().add(makeColumn("ALT", V->V.getAlternateAlleles().stream().map(A->A.getDisplayString()).collect(Collectors.joining(","))));
        table.getColumns().add(makeColumn("FILTER", V->V.getFilters().stream().collect(Collectors.joining(","))));
        table.getColumns().add(makeColumn("QUAL", V->V.hasLog10PError()?V.getPhredScaledQual():null));
        return table;
		}

	/** build INFO table */
	private TableView<InfoTableRow> buildInfoTableRow()
		{
		final TableView<InfoTableRow> table=new TableView<InfoTableRow>();
		table.getColumns().add(makeColumn("Key", R->R.key));
		table.getColumns().add(makeColumn("Index", R->R.index));
		table.getColumns().add(makeColumn("Value", R->R.value));
		return table;
		}
      
	/** build Genotype table */
	private TableView<Genotype> buildGenotypeTableRow(final VCFHeader header)
		{
		final TableView<Genotype> table=new TableView<Genotype>();
		
		/* sample */
		table.getColumns().add(makeColumn("Sample", G->G.getSampleName()));
		
		for(final VCFFormatHeaderLine h:header.getFormatHeaderLines())
			{
            final TableColumn<Genotype,String>  newcol = new TableColumn<>(h.getID());
            newcol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Genotype,String>, ObservableValue<String>>() {				
				@Override
				public ObservableValue<String> call(CellDataFeatures<Genotype, String> param) {
					Object o = param.getValue().getAnyAttribute(param.getTableColumn().getText());
					if(o==null)
						{
						return new ReadOnlyObjectWrapper<String>(null);
						}
					if(o instanceof List)
						{
						List<?> L=(List<?>)o;
						String delim=(param.getTableColumn().getText().equals(VCFConstants.GENOTYPE_KEY) && param.getValue().isPhased()?"|":",");
						o = L.stream().map(S -> String.valueOf(S)).collect(Collectors.joining(delim)).toString();
						}
					return new ReadOnlyObjectWrapper<String>(String.valueOf(o));
					}
				});
            table.getColumns().add(newcol);
			}
		/* type */
		table.getColumns().add(makeColumn("Type", G->G.getType().name()));
		return table;
		}

	
	
    private void fireSelectedVariantChanged(final VariantContext ctx)
    	{
    	refreshGenotypeTable(ctx);
    	reloadInfoTable(ctx);
    	if(ctx!=null)
    		{
    		
    		
    		this.filterTableRow.getItems().setAll(
    				ctx.getFilters()
    				);
    		
    		}
    	else
    		{
        	this.filterTableRow.getItems().clear();
    		}
    	this.genotypeChartPane.setCenter(this.makeGenotypePie(ctx));
    	}
	
    private PieChart makeGenotypePie(final VariantContext ctx) {
        final Counter<GenotypeType> countTypes= new Counter<>();
        if(ctx!=null) {
        	for(final Genotype g:ctx.getGenotypes())
				{
        		countTypes.incr(g.getType());
				}
        	}
    	final ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
    	for(final GenotypeType t:GenotypeType.values())
    		{
    		int c= (int)countTypes.count(t);
    		if(c==0) continue;
    		pieChartData.add(new PieChart.Data(
    				t.name()+" = "+c,
    				c));
    		}
        final PieChart chart = new PieChart(pieChartData);
        chart.setLegendVisible(false);
        return chart;
    	}
    
	/** build a table describing the FORMAT column */
	private Tab buildFormatHeaderTab(final VCFHeader header)
		{
        final TableView<VCFFormatHeaderLine> table=new TableView<>(FXCollections.observableArrayList(header.getFormatHeaderLines()));
        table.getColumns().add(makeColumn("ID", F->F.getID()));
        table.getColumns().add(makeColumn("Type", F->F.getType()==null?null:F.getType().name()));
        table.getColumns().add(makeColumn("Count", F->F.isFixedCount()?F.getCount():null));
        table.getColumns().add(makeColumn("Description", F->F.getDescription()));
        final Tab tab=new Tab("FORMAT",table);
        tab.setClosable(false);
        return tab;
		}

	/** build a table describing the INFO column */
	private Tab buildInfoHeaderTab(final VCFHeader header)
		{
        final TableView<VCFInfoHeaderLine> table=new TableView<>(FXCollections.observableArrayList(header.getInfoHeaderLines()));
        table.getColumns().add(makeColumn("ID", F->F.getID()));
        table.getColumns().add(makeColumn("Type", F->F.getType()==null?null:F.getType().name()));
        table.getColumns().add(makeColumn("Count", F->F.isFixedCount()?F.getCount():null));
        table.getColumns().add(makeColumn("Description", F->F.getDescription()));
        final Tab tab=new Tab("INFO",table);
        tab.setClosable(false);
        return tab;
		}
	
	/** build a table describing the INFO column */
	private Tab buildFilterHeaderTab(final VCFHeader header)
		{
        final TableView<VCFFilterHeaderLine> table=new TableView<>(FXCollections.observableArrayList(header.getFilterLines()));
        table.getColumns().add(makeColumn("ID", F->F.getID()));
        final Tab tab=new Tab("FILTER",table);
        tab.setClosable(false);
        return tab;
		}
	
	
	@Override
    void closeNgsResource() {
    	CloserUtil.close(this.vcfFileReader);
    	}
	
	@Override
    void reloadData()
		{
    	final int max_items= super.maxItemsLimitSpinner.getValue();
    	final List<VariantContext> L= new ArrayList<>(max_items);
    	final String location = this.gotoField.getText().trim();
    	final CloseableIterator<VariantContext> iter;
    	
    	
    	if(location.isEmpty())
    		{
    		iter = this.vcfFileReader.iterator();
    		}
    	else
    		{
    		final Interval interval=this.parseInterval(location);
    		if(interval==null)
    			{
    			iter=null;
    			}
    		else
    			{
				iter=this.vcfFileReader.query(interval.getContig(),interval.getStart(),interval.getEnd());
    			}
    		}
    	CompiledScript compiledScript=null;
    	SimpleBindings binding=null;
    	if(this.owner.javascriptEngine!=null && !this.javascriptArea.getText().trim().isEmpty())
    		{
    		try
    			{
    			binding=new SimpleBindings();
    			compiledScript = this.owner.javascriptEngine.compile(this.javascriptArea.getText());
    			binding.put("header", this.vcfFileReader.getFileHeader());
    			}
    		catch(Exception err)
    			{
    			LOG.warning(err.getMessage());
    			compiledScript=null;
    			}
    		}
    	final String filterName = this.javascriptFILTERfield.getText().trim();
    	int count_items=0;
    	while(iter!=null && iter.hasNext() && count_items<max_items)
    		{
    		VariantContext rec = iter.next();
    		++count_items;
    		if(compiledScript!=null)
    			{
    			binding.put("variant", rec);
    			if(!super.accept(compiledScript,binding))
    				{
    				if(filterName.isEmpty()) continue;
    				rec=new VariantContextBuilder(rec).filter(filterName).make();
    				}
    			}
    		
    		L.add(rec);
    		}
    	if(iter!=null) iter.close();
    	this.variantTable.getItems().setAll(L);
    	}
	
	private void reloadInfoTable(final VariantContext ctx)
		{
		if(ctx==null)
			{
			this.infoTableRow.getItems().clear();
			}
		else
			{
			final String filterStr=this.tfFilterInfo.getText();
			Pattern regex=null;
			if(!filterStr.trim().isEmpty())
				{
				try
					{
					regex=Pattern.compile(filterStr.trim(),Pattern.CASE_INSENSITIVE);
					}
				catch(PatternSyntaxException err)
					{
					LOG.warning("Invalid regexp :"+filterStr);
					regex=Pattern.compile(Pattern.quote(filterStr));
					}
				}
			final List<InfoTableRow> infos=new ArrayList<>();
			final Map<String,Object> atts = ctx.getAttributes();
			for(final String key:atts.keySet())
				{
				Object v= atts.get(key);
				final List<?> L;
				if(v instanceof List)
					{
					L=(List<?>)v;
					}
				else if(v.getClass().isArray())
					{
					Object a[]=(Object[])v;
					L=Arrays.asList(a);
					}
				else
					{
					L=Collections.singletonList(v);
					}
				for(int x=0;x< L.size();++x)
					{
					final InfoTableRow itr=new InfoTableRow(key,(L.size()==1?null:x+1),L.get(x));
					if(regex!=null && !itr.accept(regex)) {
						continue;
					}
					infos.add(itr);
					}
				}
			this.infoTableRow.getItems().setAll(infos);
			}
		
		}

	private void refreshGenotypeTable(final VariantContext ctx)
		{
		if(ctx==null)
			{
        	this.genotypeTable.getItems().clear();
			}
		else
			{
        	this.genotypeTable.getItems().setAll(
        			ctx.getGenotypes().stream().
        			filter(G->cboxShowNoCall.isSelected() || G.isCalled()).
        			filter(G->cboxShowHomRef.isSelected() || !G.isHomRef()).
        			collect(Collectors.toList())
        			);
			}
		}

	@Override
	protected void doMenuShowWholeStats(final ChartFactory<?> factory) {
			CompiledScript compiledScript=null;
        	if( VcfStage.this.owner.javascriptEngine!=null &&
        		!VcfStage.this.javascriptArea.getText().trim().isEmpty())
        		{
        		try
        			{
        			compiledScript = VcfStage.this.owner.javascriptEngine.compile(VcfStage.this.javascriptArea.getText());
        			}
        		catch(Exception err)
        			{
        			LOG.warning(err.getMessage());
        			updateStatusBar(AlertType.ERROR, err);
        			return;
        			}
        		}
        	final VcfQualityStage qcstage=new VcfQualityStage(
        			(VariantContextChartFactory)factory,
        			File.class.cast(super.urlOrFile),
        			compiledScript,
        			V->true
        			);
			qcstage.show();
		}

    @Override
    protected String getSnippetResourcePath() {
    	return "/com/github/lindenb/jvarkit/tools/vcfviewgui/vcf.snippets.xml";
    	}
	
	}