/**
 ******************************************************************************
 *
 * @file       uavobjectgeneratorpython.cpp
 * @author     The OpenPilot Team, http://www.openpilot.org Copyright (C) 2010.
 * @brief      produce python code for uavobjects
 *
 * @see        The GNU Public License (GPL) Version 3
 *
 *****************************************************************************/
/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

#include "uavobjectgeneratorpython.h"
using namespace std;

bool UAVObjectGeneratorPython::generate(UAVObjectParser* parser,QString templatepath,QString outputpath) {
    // Load template and setup output directory
    pythonCodePath = QDir( templatepath + QString("flight/Modules/FlightPlan/lib"));
    pythonOutputPath = QDir( outputpath + QString("python") );
    pythonOutputPath.mkpath(pythonOutputPath.absolutePath());
    pythonCodeBase     = readFile(   pythonCodePath.absoluteFilePath("uavobject.py") );
    pythonCodeTemplate = readFile(   pythonCodePath.absoluteFilePath("uavobjecttemplate.pyt") );
    pythonImportTemplate = readFile( pythonCodePath.absoluteFilePath("uavobjs.pyt") );
    
    if (pythonCodeTemplate.isEmpty() || pythonImportTemplate.isEmpty()) {
        std::cerr << "Problem reading python templates" << endl;
        return false;
    }
    
    int objnum  = parser->getNumObjects();
    //objlist = ObjectInfo[objnum];
    ObjectInfo **objlist = new ObjectInfo*[objnum];

    // Process each object
    for (int objidx = 0; objidx < objnum; ++objidx) {
        ObjectInfo* info=parser->getObjectByIndex(objidx);
	objlist[objidx] = info;
        process_object(info);
    }
    
    generate_imports(objlist, objnum);
    copy_base();

    return true; // if we come here everything should be fine
}

/**
 * Generate the python object files
 */
bool UAVObjectGeneratorPython::process_object(ObjectInfo* info)
{
    if (info == NULL)
        return false;

    // Prepare output strings
    QString outCode = pythonCodeTemplate;

    // Replace common tags
    replaceCommonTags(outCode, info);

    // Replace the ($DATAFIELDS) tag
    QString datafields;
    for (int n = 0; n < info->fields.length(); ++n)
    {
        // Class header
        datafields.append(QString("# Field %1 definition\n").arg(info->fields[n]->name));
        datafields.append(QString("class %1Field(UAVObjectField):\n").arg(info->fields[n]->name));
        // Only for enum types
        if (info->fields[n]->type == FIELDTYPE_ENUM)
        {
            datafields.append(QString("\t# Enumeration options\n"));
            // Go through each option
            QStringList options = info->fields[n]->options;
            for (int m = 0; m < options.length(); ++m) {
                QString name = options[m].toUpper().replace(QRegExp(ENUM_SPECIAL_CHARS), "");
                if (name[0].isDigit())
                    name = QString("N%1").arg(name);
                datafields.append(QString("\t%1 = %2\n").arg(name).arg(m));
            }
        }
        // Generate element names (only if field has more than one element)
        if (info->fields[n]->numElements > 1 && !info->fields[n]->defaultElementNames)
        {
            datafields.append(QString("\t# Array element names\n"));
            // Go through the element names
            QStringList elemNames = info->fields[n]->elementNames;
            for (int m = 0; m < elemNames.length(); ++m)
            {
                QString name = elemNames[m].toUpper().replace(QRegExp(ENUM_SPECIAL_CHARS), "");
                if (name[0].isDigit())
                    name = QString("N%1").arg(name);
                datafields.append(QString("\t%1 = %2\n").arg(name).arg(m));
            }
        }
        // Constructor
        datafields.append(QString("\tdef __init__(self):\n"));
	datafields.append(QString("\t\tself.name = '%1'\n").arg(info->fields[n]->name));
        datafields.append(QString("\t\tUAVObjectField.__init__(self, %1, %2)\n\n").arg(info->fields[n]->type).arg(info->fields[n]->numElements));
    }
    outCode.replace(QString("$(DATAFIELDS)"), datafields);

    // Replace the $(DATAFIELDINIT) tag
    QString fields;
    for (int n = 0; n < info->fields.length(); ++n)
    {
        fields.append(QString("\t\tself.%1 = %1Field()\n").arg(info->fields[n]->name));
        fields.append(QString("\t\tself.addField(self.%1)\n").arg(info->fields[n]->name));
    }
    outCode.replace(QString("$(DATAFIELDINIT)"), fields);
    
    // Replace $(ISSINGLEINST) tags
    outCode.replace(QString("$(ISSINGLEINST)"), info->isSingleInst ? "1" : "0");

    // Write the Python code
    bool res = writeFileIfDiffrent( pythonOutputPath.absolutePath() + "/" + info->namelc + ".py", outCode );
    if (!res) {
        cout << "Error: Could not write Python output files" << endl;
        return false;
    }

    return true;
}

bool UAVObjectGeneratorPython::generate_imports(ObjectInfo** objlist, int objnum)
{
    QString outCode = pythonImportTemplate;
    QString modules = QString("");
    QString objs    = QString("");
    for (int objidx = 0; objidx < objnum; ++objidx) {
	if (objidx) {
	    modules.append(", ");
	    objs.append(", ");
	}
	modules.append(objlist[objidx]->namelc);
	objs.append(   objlist[objidx]->namelc + "." + objlist[objidx]->name);
    }
    outCode.replace(QString("$(MODULELIST)"), modules);
    outCode.replace(QString("$(OBJECTLIST)"), objs);
    bool res = writeFileIfDiffrent( pythonOutputPath.absolutePath() + "/uavobjs.py", outCode );
    if (!res) {
        cout << "Error: Could not write Python output files" << endl;
        return false;
    }
    
    return true;
}

bool UAVObjectGeneratorPython::copy_base()
{
    QString outCode = pythonCodeBase;
    bool res = writeFileIfDiffrent( pythonOutputPath.absolutePath() + "/uavobject.py", outCode );
    if (!res) {
        cout << "Error: Could not write Python output files" << endl;
        return false;
    }
    
    return true;
}