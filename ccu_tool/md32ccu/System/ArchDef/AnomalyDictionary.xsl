<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:dsptk="http://www.mediatek.com/dsptk/archdef" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<dsptk:version file-version="1.09" />

<!-- ************************************************************************ -->
<!-- ******* AnomalyDictionary.xsl                                            -->
<!-- ************************************************************************ -->
<!-- ******* XML Transformation sheet for displaying the compiler XML files   -->
<!-- ******* and the processor anomaly dictionaries in an HTML formatted      -->
<!-- ******* display. It can also handle the display of the anomaly           -->
<!-- ******* dictionaries standalone.                                         -->
<!-- *******                                                                  -->
<!-- ******* Viewing anomaly dictionaries was verified using browsers:        -->
<!-- ******* Microsoft Internet Explorer 6.0                                  -->
<!-- ******* Mozilla FireFox 2.0.0.2                                          -->
<!-- *******                                                                  -->
<!-- ******* Copyright (c) 2009 MediaTek Inc. All Rights Reserved.            -->
<!-- ******* This software is protected by copyright and the information      -->
<!-- ******* contained herein is confidential.  The software may not be       -->
<!-- ******* copied and the information contained herein may not be used or   -->
<!-- ******* disclosed except with the written permission of MediaTek Inc.    -->
<!-- ******* Copyright 2007-2008 Analog Devices, Inc.  All rights reserved.   -->
<!-- ************************************************************************ -->

  <xsl:template match ="/">
	<html xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<head>
	<style>
	p {font-size: 140%; font-weight: bold}
	</style>
	</head>

	<body bgcolor="#99cccc">
	<xsl:apply-templates select="dsptk-dictionary-xml"/>
	<xsl:apply-templates select="dsptk-compiler-xml"/>
	</body>
	</html>
  </xsl:template>

  <!-- ****************************************** -->
  <!-- ******  dsptk-dictionary-xml   ******* -->
  <!-- ******       *-anomaly.xml         ******* -->
  <!-- ****************************************** -->

  <xsl:template match="dsptk-dictionary-xml">
	<h2 align="center">DSP Toolkit Silicon Anomaly Support</h2>
	<h4 align="center"><xsl:value-of select="@name"/></h4>
	<xsl:apply-templates select="version"/>
	<xsl:apply-templates select="anomaly-dictionary"/>
	<xsl:apply-templates select="compiler-notes"/>
	<xsl:apply-templates select="assembler-notes"/>
  </xsl:template>

  <!-- ****************************************** -->
  <!-- ******   dsptk-compiler-xml    ******* -->
  <!-- ******       *-compiler.xml        ******* -->
  <!-- ****************************************** -->

  <xsl:template match="dsptk-compiler-xml">
	<h2 align="center"><xsl:value-of select="@name"/></h2>
	<xsl:apply-templates select="version"/>
	<xsl:apply-templates select="dsptk-anomaly-dictionary"/>
	<xsl:apply-templates select="anomaly-dictionary"/>
	<xsl:apply-templates select="silicon-revisions"/>
  </xsl:template>

  <xsl:template match="version">
	<h4 align="center">File Version: <xsl:value-of select="@file-version"/></h4>
  </xsl:template>

  <xsl:template match="dsptk-anomaly-dictionary">
	<!-- provide a link to access the external dictionary -->
	<br></br>
	<a>
	<xsl:attribute name="href"><xsl:value-of select="@name"/></xsl:attribute><p>Silicon Anomaly Support</p>
	</a>
  </xsl:template>

  <!-- *********************************** -->
  <!-- ******  Silicon Revisions   ******* -->
  <!-- ******   *-compiler.xml     ******* -->
  <!-- *********************************** -->

  <xsl:template match="silicon-revisions">
	<P/><HR size="4"/>
	<br></br>

	<xsl:variable name="silicon-revision-default" select="@command-line-default" />

	<!-- For silicon revisions, iterate through each silicon and (to do: intermingled chart with workarounds) -->

	<table border="2" bordercolor="black" width="50%" cellpadding="6" cellspacing="0">
	<thead bgcolor="#efd6bc">
	<tr>
       	<th width="15%" align="center"><b><big>Silicon Revision</big></b></th>
        <th width="35%" align="left"><b><big>Library Path</big></b></th>
	</tr>
	</thead>

	<xsl:for-each select="silicon">
		<tr>

		<xsl:if test="$silicon-revision-default=@revision">
			<td width="15%" bgcolor="#dec5ab" align="left"><big><xsl:value-of select="@revision"></xsl:value-of> *</big><small> [DEFAULT]</small></td>
		</xsl:if>
		<xsl:if test="$silicon-revision-default!=@revision">
		<td width="15%" align="left"><big><xsl:value-of select="@revision"></xsl:value-of></big></td>
		</xsl:if>

		<xsl:if test="string-length(@lib-path) &gt; 1">
			<td width="35%" align="left"><big><xsl:value-of select="@lib-path"></xsl:value-of></big></td>
		</xsl:if>
		</tr>
	</xsl:for-each>
	</table>

	<p></p>* <small><xsl:value-of select="$silicon-revision-default"></xsl:value-of> is the command-line default if no -si-revision switch is present</small>

  </xsl:template>

  <!-- ********************************************* -->
  <!-- ******      Anomaly Dictionary        ******* -->
  <!-- ********************************************* -->
  <!-- ******  inlined in *-compiler.xml     ******* -->
  <!-- ******  or in separate *-anomaly.xml  ******* -->
  <!-- ********************************************* -->

  <xsl:template match="anomaly-dictionary">

	<!-- provide a link to access the externally published list of anomalies appropriate for this processor family -->
	<xsl:if test="string-length(@dsptk-web-link) > 0">
		<br></br>
		<a>
		<xsl:attribute name="href"><xsl:value-of select="@dsptk-web-link"/></xsl:attribute><b><big>Web: <xsl:value-of select="@title"/></big></b>
		</a>
	</xsl:if>

	<!-- ********************************** -->
	<!-- *******     Main menu      ******* -->
	<!-- ********************************** -->

	<br></br>
	<br></br>
	<table border="1" bgcolor="#cccccc" width="50%" cellpadding="6" cellspacing="6">
	<tr align="center">
		<th rowspan="5"><p>Tools Behavior</p></th>
		<th colspan="2"><b><big><a href="#ALL">Complete Chart</a></big></b></th>
	</tr>
	<tr align="center">
		<th colspan="2"><b><big><a href="#ASSEMBLER">Assembler Only</a></big></b></th>
	</tr>
	<tr align="center">
		<th colspan="2"><b><big><a href="#COMPILER">Compiler Only</a></big></b></th>
	</tr>
	<tr align="center">
		<th colspan="2"><b><big><a href="#LIBRARY">C Runtime Library Only</a></big></b></th>
	</tr>
	</table>

	<!-- ********************************** -->
	<!-- ********    ALL CHART     ******** -->
	<!-- ********************************** -->

	<P/><HR size="4"/>
	<a name="ALL">
	<p>All Tools</p>

	<!-- ********************************************************** -->
	<!-- For "All Tools", iterate through each anomaly, displaying  -->
	<!-- the "per component" information in the table row-by-row:   -->
	<!--    Compiler                                                -->
	<!--    Assembler                                               -->
	<!--    C Runtime Libraries                                     -->
	<!-- ********************************************************** -->

	<xsl:for-each select="anomaly">
	<xsl:sort select="@id" data-type="text" order="ascending" />

		<table border="2" bordercolor="black" width="75%" cellpadding="6" cellspacing="0">
		<thead bgcolor="#efd6bc">
		<tr>
        	<th width="15%" align="center">Anomaly ID</th>
	        <th width="60%" align="left">Summary</th>
		</tr>
		</thead>

		<!-- define labels by id and index before each individual anomaly id -->
		<a><xsl:attribute name="name">ALL_<xsl:value-of select="@id"></xsl:value-of></xsl:attribute></a>
		<a><xsl:attribute name="name">ALL_IX_<xsl:value-of select="@ix"></xsl:value-of></xsl:attribute></a>

		<tr>
		<td bgcolor="#ffe6cc" align="center"><xsl:value-of select="@id"></xsl:value-of></td>
		<td bgcolor="#ffe6cc"><xsl:value-of select="@summary"></xsl:value-of></td>
		</tr>

		<!-- ************************** -->
		<!-- ****** Compiler Row ****** -->
		<!-- ************************** -->
		<tr>
        	<td width="15%" align="center" bgcolor="#af967c">Compiler</td>
		<td style="padding:0 0 0 0;">
		<table border="0" width="100%" cellpadding="6" cellspacing="0">

		<xsl:if test="string-length(@compiler-option) = 0">
			<xsl:if test="string-length(@compiler-defs) = 0">
				<xsl:if test="string-length(@compiler-behavior) = 0">
				<tr>
				<td width="60%" align="left">No compiler actions.</td>
				</tr>
				</xsl:if>
			</xsl:if>
		</xsl:if>

		<xsl:if test="string-length(@compiler-option) &gt; 1">
			<tr>
        		<td width="10%" align="left"><b>Option</b></td>
			<td width="50%" align="left"><xsl:value-of select="@compiler-option"></xsl:value-of></td>
			</tr>
		</xsl:if>

		<xsl:if test="string-length(@compiler-defs) &gt; 1">
			<tr>
		        <td width="10%" align="left"><b>Defs</b></td>
        		<td width="50%" align="left"><xsl:value-of select="@compiler-defs"></xsl:value-of></td>
			</tr>
		</xsl:if>

		<xsl:if test="string-length(@compiler-behavior) &gt; 1">
			<tr>
		        <th width="10%" align="left" valign="top">Behavior</th>
        		<td width="50%" align="left"><xsl:value-of select="@compiler-behavior"></xsl:value-of></td>
			</tr>
		</xsl:if>

		</table>
		</td>
		</tr>

		<!-- *************************** -->
		<!-- ****** Assembler Row ****** -->
		<!-- *************************** -->
		<tr>
        	<td width="15%" align="center" bgcolor="#cfb69c">Assembler</td>
		<td style="padding:0 0 0 0;">
		<table border="0" width="100%" cellpadding="6" cellspacing="0">

		<xsl:if test="string-length(@assembler-detect-option) = 0">
			<xsl:if test="string-length(@assembler-detect-defs) = 0">
				<xsl:if test="string-length(@assembler-workaround-option) = 0">
					<xsl:if test="string-length(@assembler-workaround-defs) = 0">
						<xsl:if test="string-length(@assembler-behavior) = 0">
						<tr>
						<td width="60%" align="left">No assembler actions.</td>
						</tr>
						</xsl:if>
					</xsl:if>
				</xsl:if>
			</xsl:if>
		</xsl:if>

		<xsl:if test="string-length(@assembler-detect-option) &gt; 1">
			<tr>
        		<td width="10%" align="left"><b>Detect Option</b></td>
			<td width="50%" align="left"><xsl:value-of select="@assembler-detect-option"></xsl:value-of></td>
			</tr>
		</xsl:if>

		<xsl:if test="string-length(@assembler-detect-defs) &gt; 1">
			<tr>
        		<td width="10%" align="left"><b>Detect Defs</b></td>
			<td width="50%" align="left"><xsl:value-of select="@assembler-detect-defs"></xsl:value-of></td>
			</tr>
		</xsl:if>

		<xsl:if test="string-length(@assembler-workaround-option) &gt; 1">
			<tr>
        		<td width="10%" align="left"><b>Workaround Option</b></td>
			<td width="50%" align="left"><xsl:value-of select="@assembler-workaround-option"></xsl:value-of></td>
			</tr>
		</xsl:if>

		<xsl:if test="string-length(@assembler-workaround-defs) &gt; 1">
			<tr>
        		<td width="10%" align="left"><b>Workaround Defs</b></td>
			<td width="50%" align="left"><xsl:value-of select="@assembler-workaround-defs"></xsl:value-of></td>
			</tr>
		</xsl:if>

		<xsl:if test="string-length(@assembler-behavior) &gt; 1">
			<tr>
        		<td width="10%" align="left" valign="top"><b>Behavior</b></td>
			<td width="50%" align="left"><xsl:value-of select="@assembler-behavior"></xsl:value-of></td>
			</tr>
		</xsl:if>

		</table>
		</td>
		</tr>

		<!-- ************************************* -->
		<!-- ****** C Runtime Libraries Row ****** -->
		<!-- ************************************* -->
		<tr>
        	<td width="15%" align="center" bgcolor="#af968c">Libraries</td>
		<td style="padding:0 0 0 0;">
		<table border="0" width="100%" cellpadding="6" cellspacing="0">

		<xsl:if test="string-length(@rtl-defs) = 0">
			<xsl:if test="string-length(@rtl-behavior) = 0">
				<tr>
				<td width="60%" align="left">The runtime libraries have been built to be safe with respect to this anomaly.</td>
				</tr>
			</xsl:if>
		</xsl:if>

		<xsl:if test="string-length(@rtl-defs) &gt; 1">
			<tr>
        		<td width="10%" align="left"><b>Defs</b></td>
			<td width="50%" align="left"><xsl:value-of select="@rtl-defs"></xsl:value-of></td>
			</tr>
		</xsl:if>

		<xsl:if test="string-length(@rtl-behavior) &gt; 1">
			<tr>
		        <th width="10%" align="left" valign="top">Behavior</th>
        		<td width="50%" align="left"><xsl:value-of select="@rtl-behavior"></xsl:value-of></td>
			</tr>
		</xsl:if>

		</table>
		</td>
		</tr>

		</table>

		<br></br>

	</xsl:for-each>
</a>

	<!-- ********************************** -->
	<!-- *******  Assembler Chart   ******* -->
	<!-- ********************************** -->

	<p/><hr size="4"/>
	<a name="ASSEMBLER">

	<p>Assembler Only</p>

	<!-- ******************************************************* -->
	<!-- *** Display only those anomalies that the assembler *** -->
	<!-- *** takes some type of action for. Skip all others  *** -->
	<!-- *** when displaying the "assembly only" table       *** -->
	<!-- ******************************************************* -->

	<xsl:for-each select="anomaly[string-length(@assembler-detect-option) > 0 or string-length(@assembler-detect-defs) > 0 or string-length(@assembler-workaround-option) > 0 or string-length(@assembler-workaround-defs) > 0 or string-length(@assembler-behavior) > 0]">
	<xsl:sort select="@id" data-type="text" order="ascending" />

		<table border="2" bordercolor="black" width="75%" cellpadding="6" cellspacing="0">
		<thead bgcolor="#efd6bc">
		<tr>
        	<th width="15%" align="center">Anomaly ID</th>
	        <th width="60%" align="left">Summary</th>
		</tr>
		</thead>

		<a><xsl:attribute name="name">ASM_<xsl:value-of select="@id"></xsl:value-of></xsl:attribute></a>
		<tr>
		<td bgcolor="#ffe6cc" align="center"><xsl:value-of select="@id"></xsl:value-of></td>
		<td bgcolor="#ffe6cc"><xsl:value-of select="@summary"></xsl:value-of></td>
		</tr>

		<tr>
        	<td width="15%" align="center" bgcolor="#cfb69c">Assembler</td>
		<td style="padding:0 0 0 0;">
		<table border="0" width="100%" cellpadding="6" cellspacing="0">

		<xsl:if test="string-length(@assembler-detect-option) &gt; 1">
			<tr>
        		<td width="10%" align="left"><b>Detect Option</b></td>
			<td width="50%" align="left"><xsl:value-of select="@assembler-detect-option"></xsl:value-of></td>
			</tr>
		</xsl:if>

		<xsl:if test="string-length(@assembler-detect-defs) &gt; 1">
			<tr>
        		<td width="10%" align="left"><b>Detect Defs</b></td>
			<td width="50%" align="left"><xsl:value-of select="@assembler-detect-defs"></xsl:value-of></td>
			</tr>
		</xsl:if>

		<xsl:if test="string-length(@assembler-workaround-option) &gt; 1">
			<tr>
        		<td width="10%" align="left"><b>Workaround Option</b></td>
			<td width="50%" align="left"><xsl:value-of select="@assembler-workaround-option"></xsl:value-of></td>
			</tr>
		</xsl:if>

		<xsl:if test="string-length(@assembler-workaround-defs) &gt; 1">
			<tr>
        		<td width="10%" align="left"><b>Workaround Defs</b></td>
			<td width="50%" align="left"><xsl:value-of select="@assembler-workaround-defs"></xsl:value-of></td>
			</tr>
		</xsl:if>

		<xsl:if test="string-length(@assembler-behavior) &gt; 1">
			<tr>
        		<td width="10%" align="left" valign="top"><b>Behavior</b></td>
			<td width="50%" align="left"><xsl:value-of select="@assembler-behavior"></xsl:value-of></td>
			</tr>
		</xsl:if>

		</table>
		</td>
		</tr>

		</table>

		<br></br>

	</xsl:for-each>

</a>

	<!-- ********************************** -->
	<!-- *******  Compiler Chart   ******** -->
	<!-- ********************************** -->

	<p/><hr size="4"/>
	<a name="COMPILER">
	<p>Compiler Only</p>

	<!-- ******************************************************* -->
	<!-- *** Display only those anomalies that the compiler *** -->
	<!-- *** takes some type of action for. Skip all others  *** -->
	<!-- *** when displaying the "compiler only" table       *** -->
	<!-- ******************************************************* -->

	<xsl:for-each select="anomaly[string-length(@compiler-option) > 0 or string-length(@compiler-defs) > 0]">
	<xsl:sort select="@id" data-type="text" order="ascending" />

		<table border="2" bordercolor="black" width="75%" cellpadding="6" cellspacing="0">
		<thead bgcolor="#efd6bc">
		<tr>
        	<th width="15%" align="center">Anomaly ID</th>
	        <th width="60%" align="left">Summary</th>
		</tr>
		</thead>

		<a><xsl:attribute name="name">COMP_<xsl:value-of select="@id"></xsl:value-of></xsl:attribute></a>
		<tr>
		<td bgcolor="#ffe6cc" align="center"><xsl:value-of select="@id"></xsl:value-of></td>
		<td bgcolor="#ffe6cc"><xsl:value-of select="@summary"></xsl:value-of></td>
		</tr>

		<tr>
        	<td width="15%" align="center" bgcolor="#af967c">Compiler</td>
		<td style="padding:0 0 0 0;">
		<table border="0" width="100%" cellpadding="6" cellspacing="0">

		<xsl:if test="string-length(@compiler-option) &gt; 1">
			<tr>
        		<td width="10%" align="left"><b>Option</b></td>
			<td width="50%" align="left"><xsl:value-of select="@compiler-option"></xsl:value-of></td>
			</tr>
		</xsl:if>

		<xsl:if test="string-length(@compiler-defs) &gt; 1">
			<tr>
		        <td width="10%" align="left"><b>Defs</b></td>
        		<td width="50%" align="left"><xsl:value-of select="@compiler-defs"></xsl:value-of></td>
			</tr>
		</xsl:if>

		<xsl:if test="string-length(@compiler-behavior) &gt; 1">
			<tr>
		        <th width="10%" align="left" valign="top">Behavior</th>
        		<td width="50%" align="left"><xsl:value-of select="@compiler-behavior"></xsl:value-of></td>
			</tr>
		</xsl:if>

		</table>
		</td>
		</tr>

		</table>

		<br></br>

	</xsl:for-each>
</a>

	<!-- ********************************** -->
	<!-- *******  Libraries Chart  ******** -->
	<!-- ********************************** -->

	<p/><hr size="4"/>
	<a name="LIBRARY">
	<p>C Runtime Library Only</p>

	<!-- ******************************************************* -->
	<!-- *** Display only those anomalies that the library   *** -->
	<!-- *** takes some type of action for. Skip all others  *** -->
	<!-- *** when displaying the "library only" table        *** -->
	<!-- ******************************************************* -->

	<xsl:for-each select="anomaly[string-length(@rtl-defs) > 0 or string-length(@rtl-behavior)]">
	<xsl:sort select="@id" data-type="text" order="ascending" />

		<table border="2" bordercolor="black" width="75%" cellpadding="6" cellspacing="0">
		<thead bgcolor="#efd6bc">
		<tr>
        	<th width="15%" align="center">Anomaly ID</th>
	        <th width="60%" align="left">Summary</th>
		</tr>
		</thead>

		<a><xsl:attribute name="name">COMP_<xsl:value-of select="@id"></xsl:value-of></xsl:attribute></a>
		<tr>
		<td bgcolor="#ffe6cc" align="center"><xsl:value-of select="@id"></xsl:value-of></td>
		<td bgcolor="#ffe6cc"><xsl:value-of select="@summary"></xsl:value-of></td>
		</tr>

		<tr>
        	<td width="15%" align="center" bgcolor="#af968c">Libraries</td>
		<td style="padding:0 0 0 0;">
		<table border="0" width="100%" cellpadding="6" cellspacing="0">

		<xsl:if test="string-length(@rtl-defs) &gt; 1">
			<tr>
        		<td width="10%" align="left"><b>Defs</b></td>
			<td width="50%" align="left"><xsl:value-of select="@rtl-defs"></xsl:value-of></td>
			</tr>
		</xsl:if>

		<xsl:if test="string-length(@rtl-behavior) &gt; 1">
			<tr>
		        <th width="10%" align="left" valign="top">Behavior</th>
        		<td width="50%" align="left"><xsl:value-of select="@rtl-behavior"></xsl:value-of></td>
			</tr>
		</xsl:if>

		</table>
		</td>
		</tr>

		</table>

		<br></br>

	</xsl:for-each>
</a>

  </xsl:template>

  <!-- ********************************** -->
  <!-- *******  Compiler Notes   ******** -->
  <!-- ********************************** -->

  <xsl:template match="compiler-notes">
	<p/><hr size="4"/>
	<br></br>
	<table border="2" bordercolor="black" width="75%" cellpadding="6" cellspacing="0">
		<tr><th bgcolor="#ffe6cc" align="left"><b><big>Compiler Notes</big></b></th></tr>

		<xsl:for-each select="note">
			<xsl:if test="string-length(@text) &gt; 1">
			<tr>
				<td><xsl:value-of select="@text"></xsl:value-of></td>
			</tr>
			</xsl:if>
		</xsl:for-each>
	</table>
  </xsl:template>

  <!-- ********************************** -->
  <!-- *******  Assembler Notes   ******* -->
  <!-- ********************************** -->

  <xsl:template match="assembler-notes">
	<p/><hr size="4"/>
	<br></br>
	<table border="2" bordercolor="black" width="75%" cellpadding="6" cellspacing="0">
		<tr><th bgcolor="#ffe6cc" align="left"><b><big>Assembler Notes</big></b></th></tr>

		<xsl:for-each select="note">
			<xsl:if test="string-length(@text) &gt; 1">
			<tr>
				<td><xsl:value-of select="@text"></xsl:value-of></td>
			</tr>
			</xsl:if>
		</xsl:for-each>
	</table>
  </xsl:template>

</xsl:stylesheet>
