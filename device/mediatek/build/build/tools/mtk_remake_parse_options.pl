use strict;

my $out_file	= $ARGV[0];
my $module_file	= $ARGV[1];
my $config_file	= $ARGV[2];

my @config_options;
my @module_options;
&get_config_options(\@config_options, $config_file);
&get_module_options(\@module_options, \@config_options, $module_file);
if (1)
{
	my $WRITE_HANDLE;
	if (open($WRITE_HANDLE, ">$out_file"))
	{
		print $WRITE_HANDLE "MTK_ALL_MODULE_MAKEFILES." . $module_file . ".OPTIONS :=";
		foreach my $option (@module_options)
		{
			print $WRITE_HANDLE " \\\n    " . $option;
		}
		print $WRITE_HANDLE "\n";
		close($WRITE_HANDLE);
	}
	else
	{
		die;
	}
}

sub get_module_options
{
	my $ref_module_options = shift @_;
	my $ref_config_options = shift @_;
	my $module_file = shift @_;
	my %module_options;
	my $READ_HANDLE;
	if (open($READ_HANDLE, "<$module_file"))
	{
		my @lines = <$READ_HANDLE>;
		close($READ_HANDLE);
		foreach my $line (@lines)
		{
			$line =~ s/#.*//;
			foreach my $option (@$ref_config_options)
			{
				if ((index($line, "\$(" . $option . ")") != -1) ||
					(index($line, "\${" . $option . "}") != -1) ||
					($line =~ /(ifdef|ifndef)\s+$option\s*$/))
				{
					$module_options{$option} = 1;
				}
			}
		}
	}
	else
	{
		die;
	}
	@$ref_module_options = sort keys %module_options;
	return 0;
}

sub get_config_options
{
	my $ref_config_options = shift @_;
	my $config_file = shift @_;
	my %config_options;
	my $READ_HANDLE;
	if (open($READ_HANDLE, "<$config_file"))
	{
		my @lines = <$READ_HANDLE>;
		close($READ_HANDLE);
		foreach my $line (@lines)
		{
			$line =~ s/#.*//;
			if ($line =~ /^\s*(\S+)\s*=\s*(.*)$/)
			{
				$config_options{$1} = $2;
			}
			elsif ($line =~ /^\s*(\S+)\s*$/)
			{
				$config_options{$1} = "";
			}
		}
	}
	else
	{
		die;
	}
	@$ref_config_options = sort keys %config_options;
	return 0;
}
