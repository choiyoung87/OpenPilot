/**
 ******************************************************************************
 *
 * @file       emptycontent.h
 * @author     The OpenPilot Team, http://www.openpilot.org Copyright (C) 2013.
 * @addtogroup GCSPlugins GCS Plugins
 * @{
 * @addtogroup MonitorPlugin Monitor Plugin
 * @{
 * @brief A monitor plugins map
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

#ifndef EMPTYCONTENT_H
#define EMPTYCONTENT_H

#include "monitorcontent.h"
#include "emptycontentwidget.h"

class EmptyContent : public MonitorContent {
	Q_OBJECT
public:
	EmptyContent(CONTENT_TYPE contentType);
	virtual ~EmptyContent();

	virtual void applyConfiguration(MonitorConfiguration *config);
	virtual QGraphicsView *display() {return m_empty;}

private:
	EmptyContentWidget *m_empty;
};
#endif // EMPTYCONTENT_H
