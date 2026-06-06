set OUTPUT_DIR=c:\temp

md %OUTPUT_DIR%\forPDFFORMSZip

xcopy c:\dev\j2db\build\plugins\pdf_forms.jar %OUTPUT_DIR%\forPDFFORMSZip\plugins 
xcopy c:\dev\j2db\build\plugins\adobe_pdf_forms\jFdfTk.jar %OUTPUT_DIR%\forPDFFORMSZip\plugins\adobe_pdf_forms 
xcopy c:\dev\j2db\build\solutions\examples\pdf_forms.servoy %OUTPUT_DIR%\forPDFFORMSZip\solutions\examples
xcopy tax_form_1040a.pdf %OUTPUT_DIR%\forPDFFORMSZip\solutions\examples

cd %OUTPUT_DIR%\forPDFFORMSZip
jar cfM pdf_forms.zip *.*

