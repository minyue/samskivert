//
// $Id: HGroupLayout.java,v 1.5 2001/06/07 08:39:10 mdb Exp $

package com.samskivert.swing;

import java.awt.*;

public class HGroupLayout extends GroupLayout
{
    public HGroupLayout (int policy, int offpolicy, int gap,
			 int justification)
    {
	_policy = policy;
	_offpolicy = offpolicy;
	_gap = gap;
	_justification = justification;
    }

    public HGroupLayout (int policy, int gap, int justification)
    {
	_policy = policy;
	_gap = gap;
	_justification = justification;
    }

    public HGroupLayout (int policy, int justification)
    {
	_policy = policy;
	_justification = justification;
    }

    public HGroupLayout (int policy)
    {
	_policy = policy;
    }

    public HGroupLayout ()
    {
    }

    protected Dimension getLayoutSize (Container parent, int type)
    {
	DimenInfo info = computeDimens(parent, type);
	Dimension dims = new Dimension();

	switch (_policy) {
	case STRETCH:
	case EQUALIZE:
	    dims.width = info.maxwid * (info.count - info.numfix) +
		info.fixwid + _gap * info.count;
	    break;

	case NONE:
	default:
	    dims.width = info.totwid + _gap * info.count;
	    break;
	}

	dims.width -= _gap;
	dims.height = info.maxhei;

	// account for the insets
	Insets insets = parent.getInsets();
	dims.width += insets.left + insets.right;
	dims.height += insets.top + insets.bottom;

	return dims;
    }

    public void layoutContainer (Container parent)
    {
	Rectangle b = parent.getBounds();
	DimenInfo info = computeDimens(parent, PREFERRED);

	// adjust the bounds width and height to account for the insets
	Insets insets = parent.getInsets();
	b.width -= (insets.left + insets.right);
	b.height -= (insets.top + insets.bottom);

	int nk = parent.getComponentCount();
	int sx = insets.left, sy = insets.top;
	int totwid, totgap = _gap * (info.count-1);
	int freecount = info.count - info.numfix;

	// do the on-axis policy calculations
	int defwid = 0;
	switch (_policy) {
	case STRETCH:
	    if (freecount > 0) {
		defwid = (b.width - info.fixwid - totgap) / freecount;
		totwid = b.width;
	    } else {
		totwid = info.fixwid + totgap;
	    }
	    break;

	case EQUALIZE:
	    defwid = info.maxwid;
	    totwid = info.fixwid + defwid * freecount + totgap;
	    break;

	default:
	case NONE:
	    totwid = info.totwid + totgap;
	    break;
	}

	// do the off-axis policy calculations
	int defhei = 0;
	switch (_offpolicy) {
	case STRETCH:
	    defhei = b.height;
	    break;

	case EQUALIZE:
	    defhei = info.maxhei;
	    break;

	default:
	case NONE:
	    break;
	}

	// do the justification-related calculations
	switch (_justification) {
	case CENTER:
	    sx += (b.width - totwid)/2;
	    break;
	case RIGHT:
	case BOTTOM:
	    sx += b.width - totwid - insets.right;
	    break;
	}

	// do the layout
	for (int i = 0; i < nk; i++) {
	    // skip non-visible kids
	    if (info.dimens[i] == null) {
		continue;
	    }

	    Component child = parent.getComponent(i);
	    int newwid = defwid;
	    int newhei = defhei;

	    if (_policy == NONE || isFixed(child)) {
		newwid = info.dimens[i].width;
	    }

	    if (_offpolicy == NONE) {
		newhei = info.dimens[i].height;
	    }

            // determine our off-axis position
            switch (_offjust) {
            case LEFT:
            case TOP:
		sy = insets.top;
                break;

            case RIGHT:
            case BOTTOM:
		sy = b.height - newhei - insets.bottom;
                break;

            default:
            case CENTER:
		sy = insets.top + (b.height - newhei)/2;
                break;
            }

	    child.setBounds(sx, sy, newwid, newhei);
	    sx += child.getSize().width + _gap;
	}
    }
}
