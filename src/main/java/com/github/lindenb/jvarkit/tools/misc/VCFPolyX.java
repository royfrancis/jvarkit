/*
The MIT License (MIT)

Copyright (c) 2014 Pierre Lindenbaum

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


History:
* 2014 creation

*/
package com.github.lindenb.jvarkit.tools.misc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.vcf.VCFFilterHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.github.lindenb.jvarkit.util.jcommander.Launcher;
import com.github.lindenb.jvarkit.util.jcommander.Program;
import com.github.lindenb.jvarkit.util.log.Logger;
import com.github.lindenb.jvarkit.util.picard.GenomicSequence;
import com.github.lindenb.jvarkit.util.picard.SAMSequenceDictionaryProgress;
import com.github.lindenb.jvarkit.util.vcf.DelegateVariantContextWriter;
import com.github.lindenb.jvarkit.util.vcf.PostponedVariantContextWriter;
import com.github.lindenb.jvarkit.util.vcf.VariantContextWriterFactory;
import com.github.lindenb.jvarkit.util.vcf.VcfIterator;

/*
BEGIN_DOC

## Example

```
$ java  -jar dist/vcfpolyx.jar -R reference.fa input.vcf
(...)
2   1133956 .   A   G   2468.84 .   POLYX=23
2   1133956 .   A   AG  3604.25 .   POLYX=23
2   2981671 .   T   G   47.18   .   POLYX=24
(...)
```

END_DOC
*/
@Program(name="vcfpolyx",
	description="Number of repeated REF bases around POS.",
	keywords={"vcf","repeat"}
		)
public class VCFPolyX extends Launcher
	{
	private static final Logger LOG = Logger.build(VCFPolyX.class).make();


	@Parameter(names={"-o","--output"},description=OPT_OUPUT_FILE_OR_STDOUT)
	private File outputFile = null;
	@ParametersDelegate
	private PostponedVariantContextWriter.WritingVcfConfig writingVcfArgs = new PostponedVariantContextWriter.WritingVcfConfig();
	@ParametersDelegate
	private CtxWriterFactory component = new CtxWriterFactory();
	
	public static class CtxWriterFactory implements VariantContextWriterFactory
		{
		@Parameter(names={"-n","--filter"},description="if number of repeated bases is greater or equal to 'n' set a FILTER = (tag)")
		private int filterTrehsold = -1 ;
	
		@Parameter(names={"-t","--tag"},description="Tag used in INFO and FILTER columns.")
		private String polyXtag = "POLYX";
	
		@Parameter(names={"-R","--reference"},description=INDEXED_FASTA_REFERENCE_DESCRIPTION,required=true)
		private File faidx = null;
		
		@Parameter(names={"--skip-filtered"},description="Don't spend some time to calculate the tag if the variant is FILTERed")
		private boolean skip_filtered=false;

	
		private IndexedFastaSequenceFile indexedFastaSequenceFile=null;
		
		CtxWriterFactory() {
			
			}
		
		public void setReference(final File faidx) {
			this.faidx = faidx;
			}
		
		public void setTag(final String polyXtag) {
			this.polyXtag = polyXtag;
			}
		
		public void setTrehsold(final int filterTrehsold) {
			this.filterTrehsold = filterTrehsold;
			}
		
		@Override
		public int initialize() {
			if(this.polyXtag.trim().isEmpty()) {
				LOG.error("Empty tag");
				return -1;
				}
			if(this.faidx==null)
				{
				LOG.error("Undefined Reference");
				return -1;
				}
			LOG.info("opening reference "+faidx);
			try {
				this.indexedFastaSequenceFile=new IndexedFastaSequenceFile(faidx);
			} catch (final FileNotFoundException e) {
				LOG.error(e);
				return -1;
				}
			return 0;
			}
		@Override
		public VariantContextWriter open(final VariantContextWriter delegate) {
			return new DelegateVariantContextWriter(delegate)
					{
					private GenomicSequence genomicSequence=null;
					private VCFFilterHeaderLine filterHeaderLine = null;
					private VCFInfoHeaderLine infoHeaderLine = null;
					private final boolean skip_filtered =  CtxWriterFactory.this.skip_filtered;
					@Override
					public void writeHeader(final VCFHeader header) {

						final VCFHeader h2=new VCFHeader(header);
						
						
						this.infoHeaderLine = new VCFInfoHeaderLine(
								CtxWriterFactory.this.polyXtag.trim(),
								1,
								VCFHeaderLineType.Integer,
								"Number of repeated bases around REF")
								;
						h2.addMetaDataLine(this.infoHeaderLine);

						this.filterHeaderLine = new VCFFilterHeaderLine(
								infoHeaderLine.getID()+"_ge_"+CtxWriterFactory.this.filterTrehsold,
								"Number of repeated bases around REF is greater or equal to " +
										CtxWriterFactory.this.filterTrehsold)
								;
						
						if( CtxWriterFactory.this.filterTrehsold>-1) {
							h2.addMetaDataLine(this.filterHeaderLine);
							}

						getDelegate().writeHeader(h2);						
						}
					
					@Override
					public void add(final VariantContext ctx) {
						
						if(this.skip_filtered && ctx.isFiltered())
							{
							super.add(ctx);
							return;
							}
						
						final VariantContextBuilder b = new VariantContextBuilder(ctx);

						
						if(this.genomicSequence==null || !ctx.getContig().equals(genomicSequence.getChrom()))
							{
							LOG.info("loading chromosome "+ctx.getContig());
							this.genomicSequence=new GenomicSequence(CtxWriterFactory.this.indexedFastaSequenceFile, ctx.getContig());
							}
						int pos0=ctx.getStart()-1;
						int count=1;
						char c0=Character.toUpperCase(genomicSequence.charAt(pos0));
						//go left
						pos0--;
						while(pos0>=0 && c0==Character.toUpperCase(genomicSequence.charAt(pos0)))
							{
							++count;
							pos0--;
							}
						//go right
						pos0=ctx.getEnd()-1;
						c0=Character.toUpperCase(this.genomicSequence.charAt(pos0));
						pos0++;
						while(pos0< genomicSequence.getSAMSequenceRecord().getSequenceLength()
							&& c0==Character.toUpperCase(genomicSequence.charAt(pos0)))
							{
							++count;
							++pos0;
							}
						b.attribute(infoHeaderLine.getID(),count);
						
						/* filter */
						if(CtxWriterFactory.this.filterTrehsold>-1 && count>=CtxWriterFactory.this.filterTrehsold)
							{
							b.filter(this.filterHeaderLine.getID());
							}
						
						getDelegate().add(b.make());						
						}
					
					@Override
					public void close() {
						super.close();
						genomicSequence=null;
						}
					};
			}
		
		@Override
		public void close() throws IOException {
			CloserUtil.close(this.indexedFastaSequenceFile);
			indexedFastaSequenceFile=null;
			}
		}
	
	public VCFPolyX()
		{
		}
	
	@Override
	protected VariantContextWriter openVariantContextWriter(final File outorNull) throws IOException {
		return new PostponedVariantContextWriter(this.writingVcfArgs,stdout(),this.outputFile);
		}

	
	
	@Override
	protected int doVcfToVcf(
			final String inputName,
			final VcfIterator r,
			final VariantContextWriter delegate
			) 
		{
		
		final VariantContextWriter w= this.component.open(delegate);

		w.writeHeader(r.getHeader());
		final SAMSequenceDictionaryProgress progress= new SAMSequenceDictionaryProgress(r.getHeader()).logger(LOG);
		while(r.hasNext())
			{
			w.add(progress.watch(r.next()));
			}
		progress.finish();
		w.close();
		return RETURN_OK;
		}
	
	@Override
	public int doWork(final List<String> args) {
		
		try {
			if(this.component.initialize()!=0) {
				return -1;
				}
			
			this.writingVcfArgs.dictionary(this.component.indexedFastaSequenceFile.getSequenceDictionary());
			return doVcfToVcf(args,outputFile);
		} catch (Exception e) {
			LOG.error(e);
			return -1;
		}
		finally
			{
			CloserUtil.close(this.component);
			this.component=null;
			}
		}


	public static void main(String[] args)
		{
		new VCFPolyX().instanceMainWithExit(args);
		}

}
